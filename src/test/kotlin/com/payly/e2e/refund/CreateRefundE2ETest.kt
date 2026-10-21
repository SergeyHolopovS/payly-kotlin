package com.payly.e2e.refund

import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.PaymentStatus
import com.payly.refund.domain.model.RefundStatus
import com.payly.refund.infrastructure.web.dto.request.CreateRefundRequest
import com.payly.support.AbstractE2ETest
import com.payly.support.data.TestMerchants
import com.payly.support.fixtures.TestCards
import com.payly.support.fixtures.aCardRequest
import com.payly.support.fixtures.aCreatePaymentRequest
import com.payly.support.fixtures.aCreateRefundRequest
import com.payly.support.kafka.KafkaTestConsumer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * E2E-тесты возврата глазами клиента API: платёж честно создаётся через POST
 * (исход определяется тестовой картой), затем возвращается по HTTP с реальным
 * API-ключом. Событие о возврате проверяется в Kafka.
 *
 * Все граничные случаи суммы и статусов покрыты интеграционными тестами;
 * здесь — что весь путь "оплатил → вернул частями → вернул остаток" собран правильно.
 */
class CreateRefundE2ETest : AbstractE2ETest() {

    private val REFUND_SUCCEEDED = OutboxEventType.REFUND_SUCCEEDED.name

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
    fun `оплаченный платёж возвращается частями до полного возврата`() {
        val payment = createPaymentViaApi(ownerKey, aCreatePaymentRequest(amount = 10_000))

        KafkaTestConsumer(topic).use { kafka ->
            // 1. Частичный возврат
            refund(payment.id, aCreateRefundRequest(amount = 4_000), authorization = "Bearer $ownerKey")
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.amount").isEqualTo(4_000)
                .jsonPath("$.status").isEqualTo(RefundStatus.SUCCEEDED.name)

            assertPaymentViaApi(payment.id, PaymentStatus.PARTIALLY_REFUNDED, refundedAmount = 4_000)

            // 2. Возврат остатка — без тела запроса
            refund(payment.id, request = null, authorization = "Bearer $ownerKey")
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.amount").isEqualTo(6_000)

            assertPaymentViaApi(payment.id, PaymentStatus.REFUNDED, refundedAmount = 10_000)

            // 3. В Kafka по платежу пришли оба REFUND_SUCCEEDED, в порядке создания:
            // ключ сообщения — id платежа, значит события в одной партиции
            val refundAmounts = kafka
                .awaitRecords(minCount = 2) { it.key() == payment.id && eventType(it.value()) == REFUND_SUCCEEDED }
                .map { objectMapper.readTree(it.value())["payload"]["amount"].asString() }
            assertThat(refundAmounts).containsExactly("4000", "6000")
        }
    }

    @Test
    fun `полностью возвращённый платёж вернуть ещё раз нельзя`() {
        val payment = createPaymentViaApi(ownerKey)
        refund(payment.id, request = null, authorization = "Bearer $ownerKey").expectStatus().isCreated

        refund(payment.id, request = null, authorization = "Bearer $ownerKey")
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `неуспешный платёж вернуть нельзя — деньги не списывались`() {
        val payment = createPaymentViaApi(
            ownerKey,
            aCreatePaymentRequest(card = aCardRequest(number = TestCards.DECLINED)),
        )
        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED.name)

        refund(payment.id, request = null, authorization = "Bearer $ownerKey")
            .expectStatus().isEqualTo(409)
    }

    @Test
    fun `другой продавец не может вернуть деньги по чужому платежу`() {
        val payment = createPaymentViaApi(ownerKey)

        refund(payment.id, request = null, authorization = "Bearer $otherKey")
            .expectStatus().isNotFound

        // Владелец видит платёж нетронутым
        assertPaymentViaApi(payment.id, PaymentStatus.SUCCEEDED, refundedAmount = 0)
    }

    @Test
    fun `без API-ключа возврат не проходит аутентификацию`() {
        val payment = createPaymentViaApi(ownerKey)

        refund(payment.id, request = null, authorization = null)
            .expectStatus().isForbidden
    }

    // ---------- Хелперы ----------

    private fun eventType(message: String): String = objectMapper.readTree(message)["eventType"].asString()

    /**
     * POST возврата по HTTP. `request = null` — без тела (вернуть весь остаток);
     * `authorization = null` — без заголовка Authorization.
     */
    private fun refund(
        paymentId: String,
        request: CreateRefundRequest?,
        authorization: String?,
    ): RestTestClient.ResponseSpec =
        client.post().uri("/api/v1/payment/{id}/refund", paymentId)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .apply {
                authorization?.let { header("Authorization", it) }
                request?.let { contentType(MediaType.APPLICATION_JSON).body(it) }
            }
            .exchange()

    /** Проверяет платёж так, как его видит клиент, — через GET от имени владельца. */
    private fun assertPaymentViaApi(paymentId: String, status: PaymentStatus, refundedAmount: Long) {
        client.get().uri("/api/v1/payment/{id}", paymentId)
            .header("Authorization", "Bearer $ownerKey")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo(status.name)
            .jsonPath("$.refundedAmount").isEqualTo(refundedAmount)
    }

}
