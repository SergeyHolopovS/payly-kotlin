package com.payly.unit.webhook

import com.payly.outbox.domain.model.OutboxEventType
import com.payly.outbox.infrastucture.messaging.OutboxEventMessage
import com.payly.support.UnitTest
import com.payly.support.data.TestMerchants
import com.payly.webhook.infrastructure.messaging.OutboxEventDto
import com.payly.webhook.infrastructure.messaging.PaymentStatusEventConsumer
import com.payly.webhook.infrastructure.persistense.entity.DeliveryStatus
import com.payly.webhook.infrastructure.persistense.entity.WebhookDeliveryJpaEntity
import com.payly.webhook.infrastructure.persistense.repository.WebhookDeliveryJpaRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Unit-тесты [PaymentStatusEventConsumer]: из события outbox в Kafka получается
 * запись webhook_deliveries с телом будущего вебхука. Репозиторий — мок,
 * ObjectMapper настоящий: проверяется именно JSON, который увидит продавец.
 */
@UnitTest
class PaymentStatusEventConsumerTest {

    @Mock
    private lateinit var repository: WebhookDeliveryJpaRepository

    private val objectMapper = jacksonObjectMapper()

    private lateinit var consumer: PaymentStatusEventConsumer

    private val merchantId = UUID.fromString(TestMerchants.DEFAULT_MERCHANT_ID)

    @BeforeEach
    fun setUp() {
        consumer = PaymentStatusEventConsumer(repository, objectMapper)
    }

    @Test
    fun `формат OutboxEventMessage читается как OutboxEventDto без потерь`() {
        // Контракт между publisher и консьюмером: DTO должен зеркалить конверт
        val message = aMessage()

        val dto = objectMapper.readValue(objectMapper.writeValueAsString(message), OutboxEventDto::class.java)

        assertThat(dto.eventId).isEqualTo(message.eventId)
        assertThat(dto.eventType).isEqualTo(message.eventType)
        assertThat(dto.aggregateId).isEqualTo(message.aggregateId)
        assertThat(dto.payload).isEqualTo(message.payload)
        assertThat(dto.occurredAt).isEqualTo(message.occurredAt)
        assertThat(dto.merchantId).isEqualTo(merchantId)
    }

    @Test
    fun `создаёт доставку для продавца из payload в статусе PENDING`() {
        val before = Instant.now()
        consumer.onEvent(aRecord(aMessage(eventType = OutboxEventType.PAYMENT_SUCCEEDED.name)))

        val delivery = savedDelivery()
        assertThat(delivery.merchantId).isEqualTo(merchantId)
        assertThat(delivery.eventType).isEqualTo(OutboxEventType.PAYMENT_SUCCEEDED.name)
        assertThat(delivery.status).isEqualTo(DeliveryStatus.PENDING)
        assertThat(delivery.attempts).isZero()
        // Первая попытка — сразу, без задержки
        assertThat(delivery.nextAttemptAt).isBetween(before, Instant.now())
    }

    @Test
    fun `тело вебхука содержит id события, тип и данные платежа`() {
        val payload = mapOf(
            "paymentId" to "pay_1",
            "merchantId" to TestMerchants.DEFAULT_MERCHANT_ID,
            "amount" to "10000",
            "currency" to "RUB",
            "status" to "SUCCEEDED",
        )
        consumer.onEvent(aRecord(aMessage(eventType = OutboxEventType.PAYMENT_SUCCEEDED.name, payload = payload)))

        val body = objectMapper.readTree(savedDelivery().payload)
        assertThat(body.get("id").asString()).matches("evt_[0-9a-f-]{36}")
        assertThat(body.get("type").asString()).isEqualTo(OutboxEventType.PAYMENT_SUCCEEDED.name)
        assertThat(body.hasNonNull("createdAt")).isTrue()
        assertThat(objectMapper.convertValue(body.get("data").get("object"), Map::class.java)).isEqualTo(payload)
    }

    @Test
    fun `каждое событие получает свой id вебхука`() {
        consumer.onEvent(aRecord(aMessage()))
        consumer.onEvent(aRecord(aMessage()))

        val deliveries = argumentCaptor<WebhookDeliveryJpaEntity>()
        verify(repository, times(2)).save(deliveries.capture())
        val ids = deliveries.allValues.map { objectMapper.readTree(it.payload).get("id").asString() }
        assertThat(ids).doesNotHaveDuplicates()
    }

    @Test
    fun `событие возврата тоже превращается в вебхук продавцу`() {
        // REFUND_SUCCEEDED публикуется в тот же топик, что и события платежа,
        // и продавец должен узнать о возврате так же, как о платеже
        val payload = mapOf(
            "refundId" to "re_1",
            "merchantId" to TestMerchants.DEFAULT_MERCHANT_ID,
            "paymentId" to "pay_1",
            "amount" to "3000",
            "status" to "SUCCEEDED",
        )

        consumer.onEvent(aRecord(aMessage(eventType = OutboxEventType.REFUND_SUCCEEDED.name, payload = payload)))

        val delivery = savedDelivery()
        assertThat(delivery.eventType).isEqualTo(OutboxEventType.REFUND_SUCCEEDED.name)
        assertThat(delivery.merchantId).isEqualTo(merchantId)
    }

    // ---------- Хелперы ----------

    private fun aMessage(
        eventType: String = OutboxEventType.PAYMENT_CREATED.name,
        payload: Map<String, String> = mapOf(
            "paymentId" to "pay_1",
            "merchantId" to TestMerchants.DEFAULT_MERCHANT_ID,
        ),
    ) = OutboxEventMessage(
        eventId = UUID.randomUUID(),
        eventType = eventType,
        aggregateId = "pay_1",
        payload = payload,
        occurredAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
    )

    private fun aRecord(message: OutboxEventMessage) =
        ConsumerRecord("payment.events", 0, 0L, message.aggregateId, objectMapper.writeValueAsString(message))

    private fun savedDelivery(): WebhookDeliveryJpaEntity {
        val captor = argumentCaptor<WebhookDeliveryJpaEntity>()
        verify(repository).save(captor.capture())
        return captor.firstValue
    }

}
