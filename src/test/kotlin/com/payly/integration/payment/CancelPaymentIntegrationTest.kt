package com.payly.integration.payment

import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.PaymentStatus
import com.payly.support.AbstractIntegrationTest
import com.payly.support.data.TestMerchants
import com.payly.support.security.WithMockMerchant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Интеграционные тесты `POST /api/v1/payment/{id}/cancel`: контроллер,
 * идемпотентность, use case и реальная БД.
 *
 * Аутентификация — через [WithMockMerchant]. Платежи заводятся напрямую в БД:
 * через POST платёж сразу уходит в финальный статус, а отменить можно только CREATED —
 * платёж, по которому ещё не ходили в гейтвей. Гонку "отмена во время похода
 * в гейтвей" проверяет [CancelDuringProcessingIntegrationTest].
 */
@WithMockMerchant
class CancelPaymentIntegrationTest : AbstractIntegrationTest() {

    // ---------- Успешная отмена ----------

    @Test
    fun `отменяет платёж, по которому ещё не ходили в гейтвей, и пишет событие в outbox`() {
        val id = cancellablePayment()

        cancelPayment(id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.status").value(PaymentStatus.CANCELED.name))

        // Статус изменился в БД, а не только в ответе
        assertThat(statusInDb(id)).isEqualTo(PaymentStatus.CANCELED.name)
        // Событие записано в той же транзакции, что и смена статуса
        assertThat(outboxEventTypes(id)).containsExactly(OutboxEventType.PAYMENT_CANCELLED.name)
    }

    // ---------- Отказ в отмене ----------

    @Test
    fun `платёж в обработке гейтвеем не отменяется`() {
        val id = payments.createProcessing()

        // Свой код ошибки, чтобы клиент отличал "подождите результат"
        // от "отмена в этом статусе невозможна в принципе"
        cancelPayment(id)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("payment_processing"))

        assertThat(statusInDb(id)).isEqualTo(PaymentStatus.PROCESSING.name)
        assertThat(outboxEventTypes(id)).isEmpty()
    }

    // Для каждого статуса после гейтвея — отдельный запуск теста (PROCESSING проверен выше)
    @ParameterizedTest
    @EnumSource(value = PaymentStatus::class, names = ["CREATED", "PROCESSING"], mode = EnumSource.Mode.EXCLUDE)
    fun `не отменяет платёж в финальном или успешном статусе`(status: PaymentStatus) {
        val id = payments.create(status = status)

        // 409 Conflict: запрос корректен, но противоречит текущему состоянию платежа
        cancelPayment(id)
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Невозможно перевести платёж из статуса $status в статус CANCELED"))

        // Платёж не изменился, событий нет
        assertThat(statusInDb(id)).isEqualTo(status.name)
        assertThat(outboxEventTypes(id)).isEmpty()
    }

    @Test
    fun `чужой платёж не отменяется и выглядит как несуществующий`() {
        merchants.create(id = TestMerchants.OTHER_MERCHANT_ID)
        val foreignId = cancellablePayment(merchantId = TestMerchants.OTHER_MERCHANT_ID)

        cancelPayment(foreignId)
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Платёж не найден"))

        // Главное — платёж другого продавца остался как был
        assertThat(statusInDb(foreignId)).isEqualTo(PaymentStatus.CREATED.name)
        assertThat(outboxEventTypes(foreignId)).isEmpty()
    }

    @Test
    fun `несуществующий платёж возвращает 404`() {
        cancelPayment("pay_does_not_exist")
            .andExpect(status().isNotFound)
    }

    // ---------- Идемпотентность ----------

    @Test
    fun `запрос без Idempotency-Key отклоняется и платёж не отменяется`() {
        val id = cancellablePayment()

        cancelPayment(id, idempotencyKey = null)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("idempotency_key_missing"))

        assertThat(statusInDb(id)).isEqualTo(PaymentStatus.CREATED.name)
    }

    @Test
    fun `повтор с тем же Idempotency-Key возвращает сохранённый ответ, а не 409`() {
        val id = cancellablePayment()
        val key = UUID.randomUUID().toString()

        cancelPayment(id, key)
            .andExpect(status().isOk)

        // Клиент не получил ответ (таймаут) и повторил запрос: для него это успех,
        // хотя платёж уже в CANCELED и повторная отмена "по-честному" дала бы 409
        cancelPayment(id, key)
            .andExpect(status().isOk)
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andExpect(jsonPath("$.status").value(PaymentStatus.CANCELED.name))

        // Отмена выполнилась один раз — и событие одно
        assertThat(outboxEventTypes(id)).containsExactly(OutboxEventType.PAYMENT_CANCELLED.name)
    }

    @Test
    fun `повторная отмена с новым Idempotency-Key отклоняется`() {
        val id = cancellablePayment()

        cancelPayment(id).andExpect(status().isOk)

        // Новый ключ — это новый запрос, а не повтор: CANCELED → CANCELED запрещён
        cancelPayment(id)
            .andExpect(status().isConflict)

        assertThat(outboxEventTypes(id)).containsExactly(OutboxEventType.PAYMENT_CANCELLED.name)
    }

    // ---------- Хелперы ----------

    /** Платёж, который можно отменить: CREATED, в гейтвей за ним ещё не ходили. */
    private fun cancellablePayment(merchantId: String = TestMerchants.DEFAULT_MERCHANT_ID): String =
        payments.create(merchantId = merchantId, status = PaymentStatus.CREATED)

    /**
     * POST отмены. По умолчанию с новым случайным Idempotency-Key;
     * `null` — отправить запрос вовсе без заголовка. Тела у запроса нет.
     */
    private fun cancelPayment(id: String, idempotencyKey: String? = UUID.randomUUID().toString()): ResultActions =
        mockMvc.perform(
            post("/api/v1/payment/{id}/cancel", id)
                .apply { idempotencyKey?.let { header("Idempotency-Key", it) } }
        )

    private fun statusInDb(id: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM payments WHERE id = ?", String::class.java, id)!!

    private fun outboxEventTypes(id: String): List<String?> =
        jdbcTemplate.queryForList(
            "SELECT event_type FROM outbox_events WHERE aggregate_id = ? ORDER BY created_at",
            String::class.java,
            id,
        )

}
