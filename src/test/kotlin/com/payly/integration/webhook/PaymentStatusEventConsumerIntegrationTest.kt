package com.payly.integration.webhook

import com.payly.outbox.domain.model.OutboxEventType
import com.payly.outbox.infrastucture.messaging.OutboxEventMessage
import com.payly.support.AbstractIntegrationTest
import com.payly.support.data.TestMerchants
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Интеграционные тесты консьюмера вебхуков: событие, опубликованное в топик
 * outbox, через настоящую Kafka доходит до @KafkaListener и сохраняется
 * в webhook_deliveries реальной БД.
 *
 * Сообщение кладётся в Kafka напрямую, в обход outbox, — так тест не зависит
 * от интервала опроса OutboxEventPublisher.
 */
class PaymentStatusEventConsumerIntegrationTest : AbstractIntegrationTest() {

    @Value("\${outbox.publisher.topic}")
    private lateinit var topic: String

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Test
    fun `событие из Kafka сохраняется как доставка вебхука продавцу`() {
        val paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "")
        val payload = mapOf(
            "paymentId" to paymentId,
            "merchantId" to TestMerchants.DEFAULT_MERCHANT_ID,
            "amount" to "10000",
            "currency" to "RUB",
            "status" to "SUCCEEDED",
        )
        publish(paymentId, OutboxEventType.PAYMENT_SUCCEEDED.name, payload)

        val row = awaitDelivery(paymentId)
        assertThat(row["merchant_id"]).isEqualTo(UUID.fromString(TestMerchants.DEFAULT_MERCHANT_ID))
        assertThat(row["event_type"]).isEqualTo(OutboxEventType.PAYMENT_SUCCEEDED.name)

        val body = objectMapper.readTree(row["payload"] as String)
        assertThat(body.get("id").asString()).startsWith("evt_")
        assertThat(body.get("type").asString()).isEqualTo(OutboxEventType.PAYMENT_SUCCEEDED.name)
        assertThat(objectMapper.convertValue(body.get("data").get("object"), Map::class.java)).isEqualTo(payload)
    }

    @Test
    fun `каждое событие платежа даёт отдельную доставку`() {
        val paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "")
        val payload = mapOf("paymentId" to paymentId, "merchantId" to TestMerchants.DEFAULT_MERCHANT_ID)

        publish(paymentId, OutboxEventType.PAYMENT_CREATED.name, payload)
        publish(paymentId, OutboxEventType.PAYMENT_CANCELLED.name, payload)

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(eventTypesFor(paymentId)).containsExactlyInAnyOrder(
                OutboxEventType.PAYMENT_CREATED.name,
                OutboxEventType.PAYMENT_CANCELLED.name,
            )
        }
    }

    // ---------- Хелперы ----------

    private fun publish(paymentId: String, eventType: String, payload: Map<String, String>) {
        val message = OutboxEventMessage(
            eventId = UUID.randomUUID(),
            eventType = eventType,
            aggregateId = paymentId,
            payload = payload,
            occurredAt = Instant.now(),
        )
        // Тот же ключ, что ставит OutboxEventPublisher, — события платежа идут по порядку
        kafkaTemplate.send(topic, paymentId, objectMapper.writeValueAsString(message)).get()
    }

    /** Ждёт, пока консьюмер сохранит доставку по платежу, и возвращает её строку из БД. */
    private fun awaitDelivery(paymentId: String): Map<String, Any?> {
        await().atMost(Duration.ofSeconds(15)).until { eventTypesFor(paymentId).isNotEmpty() }
        return jdbcTemplate.queryForMap("SELECT * FROM webhook_deliveries WHERE payload LIKE ?", "%$paymentId%")
    }

    private fun eventTypesFor(paymentId: String): List<String?> =
        jdbcTemplate.queryForList(
            "SELECT event_type FROM webhook_deliveries WHERE payload LIKE ?", String::class.java, "%$paymentId%",
        )

}
