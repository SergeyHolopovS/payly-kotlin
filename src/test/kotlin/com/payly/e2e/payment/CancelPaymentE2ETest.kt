package com.payly.e2e.payment

import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.PaymentStatus
import com.payly.support.AbstractE2ETest
import com.payly.support.data.TestMerchants
import com.payly.support.kafka.KafkaTestConsumer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * E2E-тесты отмены платежа глазами клиента API: настоящий HTTP с реальными
 * API-ключами, событие об отмене проверяется в Kafka.
 *
 * Отменить можно только платёж в CREATED (в гейтвей за ним ещё не ходили). Через POST
 * такой платёж не получить — создание сразу доводит его до финального статуса, —
 * поэтому он заводится напрямую в БД. Все остальные шаги — по-настоящему.
 */
class CancelPaymentE2ETest : AbstractE2ETest() {

    @Value("\${outbox.publisher.topic}")
    private lateinit var topic: String

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val ownerKey = "sk_test_owner"
    private val otherKey = "sk_test_other"

    @BeforeEach
    fun createMerchants() {
        merchants.create(id = TestMerchants.DEFAULT_MERCHANT_ID, apiKey = ownerKey)
        merchants.create(id = TestMerchants.OTHER_MERCHANT_ID, apiKey = otherKey)
    }

    @Test
    fun `отмена платежа видна через API и доходит до Kafka`() {
        val id = payments.create(status = PaymentStatus.CREATED)

        KafkaTestConsumer(topic).use { kafka ->
            // 1. Отменяем
            cancelPayment(id, authorization = "Bearer $ownerKey")
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo(PaymentStatus.CANCELED.name)

            // 2. Повторное чтение показывает уже отменённый платёж
            client.get().uri("/api/v1/payment/{id}", id)
                .header("Authorization", "Bearer $ownerKey")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.status").isEqualTo(PaymentStatus.CANCELED.name)

            // 3. Outbox publisher доставил PAYMENT_CANCELLED в Kafka
            val records = kafka.awaitRecords { it.key() == id }
            val event = objectMapper.readTree(records.first().value())
            assertThat(event["eventType"].asString()).isEqualTo(OutboxEventType.PAYMENT_CANCELLED.name)
            assertThat(event["payload"]["status"].asString()).isEqualTo(PaymentStatus.CANCELED.name)
        }
    }

    @Test
    fun `платёж в обработке гейтвеем отменить нельзя`() {
        val id = payments.createProcessing()

        cancelPayment(id, authorization = "Bearer $ownerKey")
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo("payment_processing")
    }

    @Test
    fun `успешно оплаченный платёж отменить нельзя`() {
        // Платёж создаётся честно через API и сразу становится SUCCEEDED
        val created = createPaymentViaApi(ownerKey)

        cancelPayment(created.id, authorization = "Bearer $ownerKey")
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `другой продавец не может отменить чужой платёж`() {
        val id = payments.create(status = PaymentStatus.CREATED)

        cancelPayment(id, authorization = "Bearer $otherKey")
            .expectStatus().isNotFound

        // Владелец видит свой платёж нетронутым
        client.get().uri("/api/v1/payment/{id}", id)
            .header("Authorization", "Bearer $ownerKey")
            .exchange()
            .expectBody()
            .jsonPath("$.status").isEqualTo(PaymentStatus.CREATED.name)
    }

    @Test
    fun `без API-ключа отмена не проходит аутентификацию`() {
        val id = payments.create(status = PaymentStatus.CREATED)

        cancelPayment(id, authorization = null)
            .expectStatus().isForbidden
    }

    /** POST отмены по HTTP; `authorization = null` — запрос без заголовка Authorization. */
    private fun cancelPayment(id: String, authorization: String?): RestTestClient.ResponseSpec =
        client.post().uri("/api/v1/payment/{id}/cancel", id)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .apply { authorization?.let { header("Authorization", it) } }
            .exchange()

}
