package com.payly.e2e.webhook

import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.PaymentStatus
import com.payly.support.AbstractE2ETest
import com.payly.support.webhook.WebhookReceiver
import com.payly.webhook.infrastructure.persistense.entity.DeliveryStatus
import com.payly.webhook.infrastructure.utils.WebhookSigner
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

/**
 * E2E-тесты вебхуков: платёж, созданный через настоящий API, проходит путь
 * outbox → Kafka → консьюмер → webhook_deliveries → WebhookDeliveryWorker
 * и приходит POST-запросом на эндпоинт продавца ([WebhookReceiver]).
 *
 * Worker запускается раз в 10 секунд, поэтому ожидания здесь длиннее, чем в остальных e2e.
 */
class WebhookDeliveryE2ETest : AbstractE2ETest() {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val apiKey = "sk_test_webhooks"

    private val receiver = WebhookReceiver()

    @BeforeEach
    fun createMerchant() {
        merchants.create(apiKey = apiKey, webhookUrl = receiver.url)
    }

    @AfterEach
    fun stopReceiver() = receiver.close()

    @Test
    fun `продавец получает подписанный вебхук о созданном платеже`() {
        val payment = createPaymentViaApi(apiKey)

        val webhook = receiver.awaitRequests { it.body.contains(payment.id) }.first()

        // Подпись сходится с секретом продавца (whsec_test из MerchantTestData)
        val signature = webhook.signature!!
        val timestamp = signature.substringAfter("t=").substringBefore(",").toLong()
        assertThat(signature).isEqualTo(WebhookSigner.sign(timestamp, webhook.body, "whsec_test"))

        val body = objectMapper.readTree(webhook.body)
        assertThat(body.get("id").asString()).startsWith("evt_")
        assertThat(body.get("type").asString()).isEqualTo(OutboxEventType.PAYMENT_CREATED.name)
        val data = body.get("data").get("object")
        assertThat(data.get("paymentId").asString()).isEqualTo(payment.id)
        assertThat(data.get("amount").asString()).isEqualTo(payment.amount.toString())
        // PAYMENT_CREATED — снимок платежа до ответа гейтвея, финальный статус придёт отдельным событием
        assertThat(data.get("status").asString()).isEqualTo(PaymentStatus.PROCESSING.name)

        // Доставка помечена успешной и больше не будет отправляться
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val row = deliveryFor(payment.id)
            assertThat(row["status"]).isEqualTo(DeliveryStatus.DELIVERED.name)
            assertThat(row["attempts"]).isEqualTo(1)
            assertThat(row["last_response_code"]).isEqualTo(200)
        }
    }

    @Test
    fun `при ошибке на стороне продавца доставка откладывается на повтор`() {
        receiver.responseStatus = 500

        val payment = createPaymentViaApi(apiKey)
        receiver.awaitRequests { it.body.contains(payment.id) }

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            val row = deliveryFor(payment.id)
            assertThat(row["status"]).isEqualTo(DeliveryStatus.PENDING.name)
            assertThat(row["attempts"]).isEqualTo(1)
            assertThat(row["last_response_code"]).isEqualTo(500)
            // Следующая попытка — по RetrySchedule, через ~1 минуту, а не в следующем цикле worker'а
            assertThat((row["next_attempt_at"] as Timestamp).toInstant())
                .isAfter(Instant.now().plusSeconds(30))
        }
        assertThat(receiver.requests.count { it.body.contains(payment.id) }).isEqualTo(1)
    }

    @Test
    fun `продавцу без webhook_url вебхук не отправляется`() {
        merchants.create(
            id = "00000000-0000-0000-0000-0000000000aa",
            apiKey = "sk_test_no_webhook",
            webhookUrl = null,
        )

        val payment = createPaymentViaApi("sk_test_no_webhook")

        // Доставка всё равно создаётся, но worker сразу закрывает её как FAILED
        // ignoreExceptions: пока консьюмер не создал доставку, queryForMap бросает EmptyResultDataAccessException
        await().ignoreExceptions().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(deliveryFor(payment.id)["status"]).isEqualTo(DeliveryStatus.FAILED.name)
        }
        assertThat(receiver.requests).noneMatch { it.body.contains(payment.id) }
    }

    private fun deliveryFor(paymentId: String): Map<String, Any?> =
        jdbcTemplate.queryForMap(
            "SELECT * FROM webhook_deliveries WHERE payload LIKE ? AND event_type = ?",
            "%$paymentId%", OutboxEventType.PAYMENT_CREATED.name,
        )

}
