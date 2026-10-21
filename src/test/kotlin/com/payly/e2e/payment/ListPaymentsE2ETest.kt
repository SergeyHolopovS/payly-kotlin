package com.payly.e2e.payment

import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.infrastructure.web.dto.response.PaymentResponse
import com.payly.support.AbstractE2ETest
import com.payly.support.data.TestMerchants
import com.payly.support.fixtures.TestCards
import com.payly.support.fixtures.aCardRequest
import com.payly.support.fixtures.aCreatePaymentRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.util.UriBuilder

/**
 * E2E-тесты списка платежей глазами клиента API: платежи создаются через
 * настоящий POST (исход определяется тестовой картой), список читается по HTTP.
 *
 * Все комбинации фильтров проверены в интеграционных тестах; здесь — что
 * query-параметры доходят до БД через весь стек и продавцы изолированы
 * настоящим ApiKeyFilter.
 */
class ListPaymentsE2ETest : AbstractE2ETest() {

    private val ownerKey = "sk_test_owner"
    private val otherKey = "sk_test_other"

    // Тип для десериализации JSON-массива в List<PaymentResponse>
    private val paymentList = object : ParameterizedTypeReference<List<PaymentResponse>>() {}

    @BeforeEach
    fun createMerchants() {
        merchants.create(id = TestMerchants.DEFAULT_MERCHANT_ID, apiKey = ownerKey)
        merchants.create(id = TestMerchants.OTHER_MERCHANT_ID, apiKey = otherKey)
    }

    @Test
    fun `продавец видит только свои платежи`() {
        val succeeded = createPaymentViaApi(ownerKey)
        val failed = createPaymentViaApi(
            ownerKey,
            aCreatePaymentRequest(card = aCardRequest(number = TestCards.DECLINED)),
        )
        // Платёж другого продавца не должен попасть в выдачу владельца
        createPaymentViaApi(otherKey)

        val result = listPayments(authorization = "Bearer $ownerKey")

        assertThat(result.map { it.id }).containsExactlyInAnyOrder(succeeded.id, failed.id)
    }

    @Test
    fun `фильтр по статусу работает через весь стек`() {
        val succeeded = createPaymentViaApi(ownerKey)
        createPaymentViaApi(ownerKey, aCreatePaymentRequest(card = aCardRequest(number = TestCards.DECLINED)))

        val result = listPayments(authorization = "Bearer $ownerKey") {
            it.queryParam("status", PaymentStatus.SUCCEEDED.name)
        }

        assertThat(result.map { it.id }).containsExactly(succeeded.id)
        assertThat(result.map { it.status }).containsOnly(PaymentStatus.SUCCEEDED.name)
    }

    @Test
    fun `без API-ключа список не отдаётся`() {
        client.get().uri("/api/v1/payment")
            .exchange()
            .expectStatus().isForbidden
    }

    /**
     * GET списка платежей по HTTP. [query] дописывает query-параметры к URI;
     * ответ должен быть 200 и десериализуется в список.
     */
    private fun listPayments(
        authorization: String,
        query: (UriBuilder) -> UriBuilder = { it },
    ): List<PaymentResponse> =
        client.get().uri { query(it.path("/api/v1/payment")).build() }
            .header("Authorization", authorization)
            .exchange()
            .expectStatus().isOk
            .expectBody(paymentList)
            .returnResult().responseBody!!

}
