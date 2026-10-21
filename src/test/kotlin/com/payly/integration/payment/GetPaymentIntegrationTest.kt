package com.payly.integration.payment

import com.payly.payment.domain.model.PaymentStatus
import com.payly.support.AbstractIntegrationTest
import com.payly.support.data.TestMerchants
import com.payly.support.security.WithMockMerchant
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * Интеграционные тесты `GET /api/v1/payment/{id}`: контроллер, use case и
 * реальная БД. Аутентификация — через [WithMockMerchant], платежи заводятся
 * напрямую в БД через [com.payly.support.data.PaymentTestData], а не через POST, —
 * так тест не зависит от работы создания платежа.
 */
@WithMockMerchant
class GetPaymentIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `возвращает платёж продавца со всеми полями`() {
        val createdAt = Instant.parse("2026-01-10T10:00:00Z")
        val id = payments.create(
            amount = 15_000,
            currency = "USD",
            status = PaymentStatus.PARTIALLY_REFUNDED,
            description = "Order #7",
            metadataJson = """{"orderId": "7"}""",
            cardLast4 = "1111",
            refundedAmount = 5_000,
            createdAt = createdAt,
        )

        // Проверяем, что каждое поле из БД доходит до ответа API без искажений
        getPayment(id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.amount").value(15_000))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.status").value(PaymentStatus.PARTIALLY_REFUNDED.name))
            .andExpect(jsonPath("$.description").value("Order #7"))
            .andExpect(jsonPath("$.cardLast4").value("1111"))
            .andExpect(jsonPath("$.refundedAmount").value(5_000))
            .andExpect(jsonPath("$.metadata.orderId").value("7"))
            // Дата отдаётся в ISO-8601 (UTC), а не числом
            .andExpect(jsonPath("$.createdAt").value("2026-01-10T10:00:00Z"))
    }

    @Test
    fun `в ответе нет полей, которые не должны уходить клиенту`() {
        val id = payments.create(status = PaymentStatus.FAILED, failureReason = "card_declined")

        // merchantId и version — внутренние поля; failureReason в текущем API не отдаётся
        getPayment(id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.merchantId").doesNotExist())
            .andExpect(jsonPath("$.version").doesNotExist())
            .andExpect(jsonPath("$.failureReason").doesNotExist())
    }

    @Test
    fun `чужой платёж отдаётся как несуществующий`() {
        merchants.create(id = TestMerchants.OTHER_MERCHANT_ID)
        val foreignId = payments.create(merchantId = TestMerchants.OTHER_MERCHANT_ID)

        // Ответ ничем не отличается от запроса несуществующего id (см. тест ниже) —
        // по нему нельзя понять, что такой платёж вообще есть
        getPayment(foreignId)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Платёж не найден"))
    }

    @Test
    fun `несуществующий платёж возвращает 404`() {
        getPayment("pay_does_not_exist")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Платёж не найден"))
    }

    @Test
    fun `для чтения не нужен Idempotency-Key`() {
        // @Idempotent стоит только на изменяющих методах; GET без заголовка должен работать
        val id = payments.create()

        getPayment(id).andExpect(status().isOk)
    }

    private fun getPayment(id: String): ResultActions =
        mockMvc.perform(get("/api/v1/payment/{id}", id))

}
