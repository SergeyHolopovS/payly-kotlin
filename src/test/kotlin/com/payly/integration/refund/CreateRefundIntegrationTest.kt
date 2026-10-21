package com.payly.integration.refund

import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.PaymentStatus
import com.payly.refund.domain.model.RefundStatus
import com.payly.refund.infrastructure.web.dto.request.CreateRefundRequest
import com.payly.support.AbstractIntegrationTest
import com.payly.support.data.TestMerchants
import com.payly.support.fixtures.aCreateRefundRequest
import com.payly.support.security.WithMockMerchant
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Интеграционные тесты `POST /api/v1/payment/{id}/refund`: контроллер, валидация,
 * идемпотентность, use case и реальная БД.
 *
 * Аутентификация — через [WithMockMerchant]. Платежи заводятся напрямую в БД
 * с нужным статусом и уже возвращённой суммой. Конкурентные возвраты проверяет
 * [ConcurrentRefundIntegrationTest].
 */
@WithMockMerchant
class CreateRefundIntegrationTest : AbstractIntegrationTest() {

    // ---------- Полный и частичный возврат ----------

    @Test
    fun `без тела запроса возвращает весь платёж`() {
        val paymentId = payments.create(amount = 10_000, status = PaymentStatus.SUCCEEDED)

        // 201 Created — возврат это новый ресурс; тело не обязательно
        refund(paymentId, request = null)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id", startsWith("re_")))
            .andExpect(jsonPath("$.paymentId").value(paymentId))
            .andExpect(jsonPath("$.amount").value(10_000))
            .andExpect(jsonPath("$.status").value(RefundStatus.SUCCEEDED.name))

        // Платёж полностью возвращён
        assertPaymentInDb(paymentId, PaymentStatus.REFUNDED, refundedAmount = 10_000)
        assertThat(refundAmountsInDb(paymentId)).containsExactly(10_000L)
        // Событие записано в той же транзакции, что возврат и платёж
        assertThat(outboxEventTypes(paymentId)).containsExactly(OutboxEventType.REFUND_SUCCEEDED.name)
    }

    @Test
    fun `пустое тело без суммы тоже возвращает весь платёж`() {
        val paymentId = payments.create(amount = 10_000, status = PaymentStatus.SUCCEEDED)

        // {} и отсутствие тела — один и тот же смысл: amount не задан
        refund(paymentId, aCreateRefundRequest(amount = null))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.amount").value(10_000))

        assertPaymentInDb(paymentId, PaymentStatus.REFUNDED, refundedAmount = 10_000)
    }

    @Test
    fun `частичный возврат переводит платёж в PARTIALLY_REFUNDED`() {
        val paymentId = payments.create(amount = 10_000, status = PaymentStatus.SUCCEEDED)

        refund(paymentId, aCreateRefundRequest(amount = 3_000))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.amount").value(3_000))

        assertPaymentInDb(paymentId, PaymentStatus.PARTIALLY_REFUNDED, refundedAmount = 3_000)
    }

    @Test
    fun `несколько частичных возвратов и возврат остатка доводят платёж до REFUNDED`() {
        val paymentId = payments.create(amount = 10_000, status = PaymentStatus.SUCCEEDED)

        refund(paymentId, aCreateRefundRequest(amount = 3_000)).andExpect(status().isCreated)
        refund(paymentId, aCreateRefundRequest(amount = 2_000)).andExpect(status().isCreated)
        assertPaymentInDb(paymentId, PaymentStatus.PARTIALLY_REFUNDED, refundedAmount = 5_000)

        // Без суммы — возвращается именно остаток (5_000), а не сумма платежа
        refund(paymentId, request = null)
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.amount").value(5_000))

        assertPaymentInDb(paymentId, PaymentStatus.REFUNDED, refundedAmount = 10_000)
        // Сумма всех возвратов в БД сходится с суммой платежа
        assertThat(refundAmountsInDb(paymentId)).containsExactlyInAnyOrder(3_000L, 2_000L, 5_000L)
    }

    // ---------- Некорректная сумма ----------

    @Test
    fun `сумма больше остатка отклоняется и ничего не меняет`() {
        val paymentId = payments.create(
            amount = 10_000,
            status = PaymentStatus.PARTIALLY_REFUNDED,
            refundedAmount = 8_000,
        )

        refund(paymentId, aCreateRefundRequest(amount = 2_001))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Неправильная сумма возврата"))

        assertPaymentInDb(paymentId, PaymentStatus.PARTIALLY_REFUNDED, refundedAmount = 8_000)
        assertThat(refundAmountsInDb(paymentId)).isEmpty()
        assertThat(outboxEventTypes(paymentId)).isEmpty()
    }

    @Test
    fun `нулевая сумма отклоняется валидацией`() {
        val paymentId = payments.create(status = PaymentStatus.SUCCEEDED)

        // Срабатывает @Positive в CreateRefundRequest — до use case дело не доходит
        refund(paymentId, aCreateRefundRequest(amount = 0))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("amount")))

        assertThat(refundAmountsInDb(paymentId)).isEmpty()
    }

    @Test
    fun `отрицательная сумма отклоняется валидацией`() {
        val paymentId = payments.create(status = PaymentStatus.SUCCEEDED)

        refund(paymentId, aCreateRefundRequest(amount = -100))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("amount")))

        assertThat(refundAmountsInDb(paymentId)).isEmpty()
    }

    // ---------- Неподходящий платёж ----------

    // Для каждого статуса, из которого возврат невозможен, — отдельный запуск теста
    @ParameterizedTest
    @EnumSource(value = PaymentStatus::class, names = ["SUCCEEDED", "PARTIALLY_REFUNDED"], mode = EnumSource.Mode.EXCLUDE)
    fun `платёж в неподходящем статусе не возвращается`(status: PaymentStatus) {
        // PROCESSING — через createProcessing, чтобы планировщик не финализировал его посреди теста
        val paymentId =
            if (status == PaymentStatus.PROCESSING) payments.createProcessing()
            else payments.create(status = status)

        // 409 Conflict: запрос корректен, но противоречит текущему состоянию платежа
        refund(paymentId, request = null)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Платёж в статусе $status не может быть возвращён"))

        assertThat(statusInDb(paymentId)).isEqualTo(status.name)
        assertThat(refundAmountsInDb(paymentId)).isEmpty()
    }

    @Test
    fun `чужой платёж не возвращается и выглядит как несуществующий`() {
        merchants.create(id = TestMerchants.OTHER_MERCHANT_ID)
        val foreignId = payments.create(merchantId = TestMerchants.OTHER_MERCHANT_ID, status = PaymentStatus.SUCCEEDED)

        refund(foreignId, request = null)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Платёж не найден"))

        // Деньги по чужому платежу не тронуты
        assertPaymentInDb(foreignId, PaymentStatus.SUCCEEDED, refundedAmount = 0)
        assertThat(refundAmountsInDb(foreignId)).isEmpty()
    }

    @Test
    fun `несуществующий платёж возвращает 404`() {
        refund("pay_does_not_exist", request = null)
            .andExpect(status().isNotFound)
    }

    // ---------- Идемпотентность ----------

    @Test
    fun `запрос без Idempotency-Key отклоняется`() {
        val paymentId = payments.create(status = PaymentStatus.SUCCEEDED)

        refund(paymentId, request = null, idempotencyKey = null)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("idempotency_key_missing"))

        assertThat(refundAmountsInDb(paymentId)).isEmpty()
    }

    @Test
    fun `повтор с тем же Idempotency-Key не возвращает деньги второй раз`() {
        val paymentId = payments.create(amount = 10_000, status = PaymentStatus.SUCCEEDED)
        val key = UUID.randomUUID().toString()
        val request = aCreateRefundRequest(amount = 3_000)

        val firstRefundId = refund(paymentId, request, key)
            .andExpect(status().isCreated)
            .andReturn().response.contentAsString
            .let { objectMapper.readTree(it)["id"].asString() }

        // Повтор отдаёт сохранённый ответ — с тем же статусом 201 и тем же id возврата
        refund(paymentId, request, key)
            .andExpect(status().isCreated)
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andExpect(jsonPath("$.id").value(firstRefundId))

        // Самое важное для денег: возврат один, сумма списана с остатка один раз
        assertThat(refundAmountsInDb(paymentId)).containsExactly(3_000L)
        assertPaymentInDb(paymentId, PaymentStatus.PARTIALLY_REFUNDED, refundedAmount = 3_000)
    }

    @Test
    fun `тот же Idempotency-Key с другой суммой отклоняется`() {
        val paymentId = payments.create(amount = 10_000, status = PaymentStatus.SUCCEEDED)
        val key = UUID.randomUUID().toString()

        refund(paymentId, aCreateRefundRequest(amount = 3_000), key)
            .andExpect(status().isCreated)

        refund(paymentId, aCreateRefundRequest(amount = 4_000), key)
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("idempotency_key_reused"))

        assertThat(refundAmountsInDb(paymentId)).containsExactly(3_000L)
    }

    // ---------- Хелперы ----------

    /**
     * POST возврата. `request = null` — запрос без тела (вернуть весь остаток);
     * по умолчанию с новым случайным Idempotency-Key, `null` — без заголовка.
     */
    private fun refund(
        paymentId: String,
        request: CreateRefundRequest?,
        idempotencyKey: String? = UUID.randomUUID().toString(),
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/payment/{id}/refund", paymentId)
                .apply {
                    request?.let { contentType(MediaType.APPLICATION_JSON).content(toJson(it)) }
                    idempotencyKey?.let { header("Idempotency-Key", it) }
                }
        )

    private fun assertPaymentInDb(paymentId: String, status: PaymentStatus, refundedAmount: Long) {
        val row = jdbcTemplate.queryForMap("SELECT status, refunded_amount FROM payments WHERE id = ?", paymentId)
        assertThat(row["status"]).isEqualTo(status.name)
        assertThat(row["refunded_amount"]).isEqualTo(refundedAmount)
    }

    private fun statusInDb(paymentId: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM payments WHERE id = ?", String::class.java, paymentId)!!

    private fun refundAmountsInDb(paymentId: String): List<Long?> =
        jdbcTemplate.queryForList("SELECT amount FROM refunds WHERE payment_id = ?", Long::class.java, paymentId)

    private fun outboxEventTypes(paymentId: String): List<String?> =
        jdbcTemplate.queryForList(
            "SELECT event_type FROM outbox_events WHERE aggregate_id = ?", String::class.java, paymentId,
        )

}
