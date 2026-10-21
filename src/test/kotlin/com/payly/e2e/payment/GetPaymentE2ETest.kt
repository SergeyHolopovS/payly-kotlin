package com.payly.e2e.payment

import com.payly.payment.domain.model.PaymentStatus
import com.payly.support.AbstractE2ETest
import com.payly.support.data.TestMerchants
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * E2E-тесты получения платежа глазами клиента API: платёж создаётся через
 * настоящий POST, затем читается по HTTP с реальными API-ключами.
 *
 * Главное, что проверяется здесь и чего нет в интеграционных тестах, —
 * изоляция продавцов через настоящий ApiKeyFilter, а не через мок аутентификации.
 */
class GetPaymentE2ETest : AbstractE2ETest() {

    // Два продавца с разными ключами: владелец платежа и "посторонний"
    private val ownerKey = "sk_test_owner"
    private val otherKey = "sk_test_other"

    @BeforeEach
    fun createMerchants() {
        merchants.create(id = TestMerchants.DEFAULT_MERCHANT_ID, apiKey = ownerKey)
        merchants.create(id = TestMerchants.OTHER_MERCHANT_ID, apiKey = otherKey)
    }

    @Test
    fun `созданный платёж читается владельцем в том же виде, в каком был создан`() {
        val created = createPaymentViaApi(ownerKey)

        // Ответ GET совпадает с ответом POST — то, что клиент сохранил у себя
        // после создания, и то, что он прочитает потом, не расходятся
        getPayment(created.id, authorization = "Bearer $ownerKey")
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(created.id)
            .jsonPath("$.amount").isEqualTo(created.amount)
            .jsonPath("$.currency").isEqualTo(created.currency)
            .jsonPath("$.status").isEqualTo(PaymentStatus.SUCCEEDED.name)
            .jsonPath("$.cardLast4").isEqualTo(created.cardLast4)
    }

    @Test
    fun `другой продавец не видит чужой платёж`() {
        val created = createPaymentViaApi(ownerKey)

        // Ключ валиден, но платёж не его — 404, как будто платежа не существует
        getPayment(created.id, authorization = "Bearer $otherKey")
            .expectStatus().isNotFound
    }

    @Test
    fun `без API-ключа платёж не отдаётся`() {
        val created = createPaymentViaApi(ownerKey)

        getPayment(created.id, authorization = null)
            .expectStatus().isForbidden
    }

    /** GET платежа по HTTP; `authorization = null` — запрос без заголовка Authorization. */
    private fun getPayment(id: String, authorization: String?): RestTestClient.ResponseSpec =
        client.get().uri("/api/v1/payment/{id}", id)
            .apply { authorization?.let { header("Authorization", it) } }
            .exchange()

}
