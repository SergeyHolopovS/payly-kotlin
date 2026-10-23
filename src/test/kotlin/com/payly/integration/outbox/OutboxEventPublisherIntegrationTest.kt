package com.payly.integration.outbox

import com.payly.outbox.application.usecase.create.CreateOutboxEventCommand
import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.outbox.infrastucture.messaging.OutboxEventMessage
import com.payly.support.AbstractIntegrationTest
import com.payly.support.data.TestMerchants
import com.payly.support.kafka.KafkaTestConsumer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * Интеграционные тесты transactional outbox: событие, сохранённое через
 * [CreateOutboxEventUseCase], планировщик OutboxEventPublisher доставляет
 * в настоящую Kafka и помечает в БД опубликованным.
 */
class OutboxEventPublisherIntegrationTest : AbstractIntegrationTest() {

    @Value("\${outbox.publisher.topic}")
    private lateinit var topic: String

    @Autowired
    private lateinit var createOutboxEvent: CreateOutboxEventUseCase

    @Test
    fun `сохранённое событие публикуется в Kafka и помечается опубликованным`() {
        KafkaTestConsumer(topic).use { kafka ->
            // merchantId в payload — чтобы консьюмер вебхуков в этом же контексте
            // смог обработать событие и не засорял лог ошибками
            val payload = mapOf("paymentId" to "pay_outbox_it", "merchantId" to TestMerchants.DEFAULT_MERCHANT_ID)
            val event = createOutboxEvent.execute(
                CreateOutboxEventCommand(
                    aggregateId = "pay_outbox_it",
                    eventType = OutboxEventType.PAYMENT_SUCCEEDED.name,
                    payload = payload,
                )
            )

            // Только что сохранённое событие ещё не опубликовано
            assertThat(event.publishedAt).isNull()

            val record = kafka.awaitRecords { it.key() == "pay_outbox_it" }.single()
            val message = objectMapper.readValue(record.value(), OutboxEventMessage::class.java)
            assertThat(message.eventId).isEqualTo(event.id)
            assertThat(message.eventType).isEqualTo(OutboxEventType.PAYMENT_SUCCEEDED.name)
            assertThat(message.aggregateId).isEqualTo("pay_outbox_it")
            assertThat(message.payload).isEqualTo(payload)
            // Postgres хранит время с точностью до микросекунд
            assertThat(message.occurredAt).isEqualTo(event.createdAt.truncatedTo(ChronoUnit.MICROS))
        }

        // markPublished выполняется в колбэке после ack от брокера — ждём его
        await().atMost(Duration.ofSeconds(10)).untilAsserted {
            val publishedAt = jdbcTemplate.queryForObject(
                "SELECT published_at FROM outbox_events WHERE aggregate_id = ?",
                java.sql.Timestamp::class.java,
                "pay_outbox_it",
            )
            assertThat(publishedAt).isNotNull()
        }
    }

    @Test
    fun `уже опубликованное событие повторно не отправляется`() {
        // Событие, которое как будто опубликовали в прошлом цикле
        jdbcTemplate.update(
            """
            INSERT INTO outbox_events (id, aggregate_id, event_type, payload, published_at)
            VALUES (gen_random_uuid(), 'pay_already_published', ?, '{}'::jsonb, now())
            """.trimIndent(),
            OutboxEventType.PAYMENT_CREATED.name,
        )

        KafkaTestConsumer(topic).use { kafka ->
            // Маркер: событие, созданное позже. Когда оно дошло до Kafka, цикл
            // публикации точно прошёл и мог бы отправить и первое событие
            createOutboxEvent.execute(
                CreateOutboxEventCommand(
                    aggregateId = "pay_marker",
                    eventType = OutboxEventType.PAYMENT_CREATED.name,
                    payload = mapOf("merchantId" to TestMerchants.DEFAULT_MERCHANT_ID),
                )
            )
            val records = kafka.awaitRecords { it.key() == "pay_marker" || it.key() == "pay_already_published" }

            assertThat(records.map { it.key() }).containsExactly("pay_marker")
        }
    }

}
