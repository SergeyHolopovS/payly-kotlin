package com.payly.unit.outbox

import com.payly.outbox.domain.model.OutboxEvent
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.outbox.domain.repository.OutboxEventRepository
import com.payly.outbox.infrastucture.messaging.OutboxEventMessage
import com.payly.outbox.infrastucture.messaging.OutboxEventPublisher
import com.payly.outbox.infrastucture.properties.OutboxPublisherProperties
import com.payly.support.UnitTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Unit-тесты [OutboxEventPublisher]: что и куда уходит в Kafka и когда событие
 * помечается опубликованным. KafkaTemplate и репозиторий — моки, ObjectMapper настоящий,
 * чтобы проверять реальный JSON "на проводе". Доставка через настоящую Kafka —
 * в интеграционных и e2e тестах.
 */
@UnitTest
class OutboxEventPublisherTest {

    @Mock
    private lateinit var repository: OutboxEventRepository

    @Mock
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    private val objectMapper = jacksonObjectMapper()

    private val properties = OutboxPublisherProperties(topic = "test.events", batchSize = 10)

    private lateinit var publisher: OutboxEventPublisher

    @BeforeEach
    fun setUp() {
        publisher = OutboxEventPublisher(repository, kafkaTemplate, objectMapper, properties)
    }

    @Test
    fun `отправляет событие в топик из настроек с ключом aggregateId`() {
        val event = anOutboxEvent(aggregateId = "pay_1")
        givenUnpublished(event)
        givenKafkaAccepts()

        publisher.publishPending()

        // Ключ = aggregateId: все события одного платежа попадают в одну партицию
        verify(kafkaTemplate).send(eq("test.events"), eq("pay_1"), any())
    }

    @Test
    fun `сообщение содержит конверт события, а не domain-модель`() {
        val createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val event = anOutboxEvent(
            aggregateId = "pay_1",
            eventType = OutboxEventType.PAYMENT_SUCCEEDED.name,
            payload = mapOf("paymentId" to "pay_1", "status" to "SUCCEEDED"),
            createdAt = createdAt,
        )
        givenUnpublished(event)
        givenKafkaAccepts()

        publisher.publishPending()

        val message = objectMapper.readValue(sentValues().single(), OutboxEventMessage::class.java)
        assertThat(message).isEqualTo(
            OutboxEventMessage(
                eventId = event.id,
                eventType = OutboxEventType.PAYMENT_SUCCEEDED.name,
                aggregateId = "pay_1",
                payload = mapOf("paymentId" to "pay_1", "status" to "SUCCEEDED"),
                // occurredAt — момент создания события, а не публикации
                occurredAt = createdAt,
            )
        )
    }

    @Test
    fun `событие без payload уходит с пустым payload, а не с null`() {
        givenUnpublished(anOutboxEvent(payload = null))
        givenKafkaAccepts()

        publisher.publishPending()

        // Консьюмеры рассчитывают на объект в payload — null им отдавать нельзя
        val json = objectMapper.readTree(sentValues().single())
        assertThat(json.get("payload").isObject).isTrue()
        assertThat(json.get("payload").size()).isZero()
    }

    @Test
    fun `после подтверждения Kafka событие помечается опубликованным`() {
        val event = anOutboxEvent()
        givenUnpublished(event)
        givenKafkaAccepts()

        val before = Instant.now()
        publisher.publishPending()

        val publishedAt = argumentCaptor<Instant>()
        verify(repository).markPublished(eq(event.id), publishedAt.capture())
        assertThat(publishedAt.firstValue).isBetween(before, Instant.now())
    }

    @Test
    fun `если Kafka не приняла событие, оно остаётся неопубликованным`() {
        givenUnpublished(anOutboxEvent())
        whenever(kafkaTemplate.send(any<String>(), any<String>(), any<String>()))
            .thenReturn(CompletableFuture.failedFuture(RuntimeException("broker is down")))

        // Ошибка не пробрасывается из планировщика — она только логируется,
        // а событие будет отправлено повторно в следующем цикле
        publisher.publishPending()

        verify(repository, never()).markPublished(any(), any())
    }

    @Test
    fun `ошибка отправки одного события не мешает отправить остальные`() {
        val failed = anOutboxEvent(aggregateId = "pay_1")
        val ok = anOutboxEvent(aggregateId = "pay_2")
        givenUnpublished(failed, ok)
        whenever(kafkaTemplate.send(eq("test.events"), eq("pay_1"), any()))
            .thenReturn(CompletableFuture.failedFuture(RuntimeException("broker is down")))
        whenever(kafkaTemplate.send(eq("test.events"), eq("pay_2"), any()))
            .thenReturn(CompletableFuture.completedFuture(null))

        publisher.publishPending()

        verify(repository, never()).markPublished(eq(failed.id), any())
        verify(repository).markPublished(eq(ok.id), any())
    }

    @Test
    fun `события отправляются в том порядке, в котором их вернул репозиторий`() {
        val first = anOutboxEvent(aggregateId = "pay_1")
        val second = anOutboxEvent(aggregateId = "pay_1")
        givenUnpublished(first, second)
        givenKafkaAccepts()

        publisher.publishPending()

        // Репозиторий отдаёт события по createdAt — порядок внутри партиции должен сохраниться
        val eventIds = sentValues().map { objectMapper.readTree(it).get("eventId").asString() }
        assertThat(eventIds).containsExactly(first.id.toString(), second.id.toString())
    }

    @Test
    fun `вычитывает из outbox не больше batchSize событий за цикл`() {
        whenever(repository.findUnpublished(10)).thenReturn(emptyList())

        publisher.publishPending()

        verify(repository).findUnpublished(10)
        verifyNoInteractions(kafkaTemplate)
    }

    @Test
    fun `markPublished вызывается только после ответа Kafka`() {
        val event = anOutboxEvent()
        givenUnpublished(event)
        val ack = CompletableFuture<SendResult<String, String>>()
        whenever(kafkaTemplate.send(any<String>(), any<String>(), any<String>())).thenReturn(ack)

        publisher.publishPending()
        // Отправка ушла, но брокер ещё не ответил — помечать рано
        verify(repository, never()).markPublished(any(), any())

        ack.complete(null)
        inOrder(kafkaTemplate, repository) {
            verify(kafkaTemplate).send(any<String>(), any<String>(), any<String>())
            verify(repository).markPublished(eq(event.id), any())
        }
    }

    // ---------- Хелперы ----------

    private fun anOutboxEvent(
        aggregateId: String = "pay_1",
        eventType: String = OutboxEventType.PAYMENT_CREATED.name,
        payload: Map<String, String>? = mapOf("paymentId" to aggregateId),
        createdAt: Instant = Instant.now(),
    ) = OutboxEvent(
        id = UUID.randomUUID(),
        aggregateId = aggregateId,
        eventType = eventType,
        payload = payload,
        createdAt = createdAt,
    )

    private fun givenUnpublished(vararg events: OutboxEvent) {
        whenever(repository.findUnpublished(properties.batchSize)).thenReturn(events.toList())
    }

    private fun givenKafkaAccepts() {
        whenever(kafkaTemplate.send(any<String>(), any<String>(), any<String>()))
            .thenReturn(CompletableFuture.completedFuture(null))
    }

    /** JSON всех сообщений, ушедших в Kafka, в порядке отправки. */
    private fun sentValues(): List<String> {
        val values = argumentCaptor<String>()
        verify(kafkaTemplate, atLeastOnce()).send(any<String>(), any<String>(), values.capture())
        return values.allValues
    }

}
