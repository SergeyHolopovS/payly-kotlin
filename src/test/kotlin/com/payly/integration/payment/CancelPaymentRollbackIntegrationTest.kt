package com.payly.integration.payment

import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.PaymentStatus
import com.payly.support.AbstractIntegrationTest
import com.payly.support.security.WithMockMerchant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Проверяет транзакционность отмены: смена статуса и запись outbox-события
 * должны примениться вместе или не примениться вовсе. Иначе платёж оказался бы
 * отменён, а подписчики (вебхуки, Kafka) никогда бы об этом не узнали.
 *
 * Вынесен в отдельный класс, потому что @MockitoSpyBean меняет состав бинов —
 * Spring собирает под него отдельный контекст, и остальным тестам отмены
 * незачем его делить.
 */
@WithMockMerchant
class CancelPaymentRollbackIntegrationTest : AbstractIntegrationTest() {

    // Spy, а не mock: настоящий бин продолжает работать для всех остальных
    // вызовов (например, из PaymentProcessingScheduler), ломаем только нужный
    @MockitoSpyBean
    private lateinit var createOutbox: CreateOutboxEventUseCase

    @Test
    fun `если запись в outbox упала, отмена платежа откатывается`() {
        val id = payments.create(status = PaymentStatus.CREATED)

        // Падает только запись события об отмене — в момент, когда статус
        // платежа уже обновлён (update выполняется до outbox)
        doThrow(IllegalStateException("outbox is down"))
            .whenever(createOutbox)
            .execute(argThat { eventType == OutboxEventType.PAYMENT_CANCELLED.name })

        // Неожиданная ошибка → GlobalExceptionHandler → 500
        mockMvc.perform(
            post("/api/v1/payment/{id}/cancel", id)
                .header("Idempotency-Key", UUID.randomUUID().toString())
        ).andExpect(status().isInternalServerError)

        // @Transactional откатил уже выполненный update — платёж остался в исходном статусе
        val status = jdbcTemplate.queryForObject("SELECT status FROM payments WHERE id = ?", String::class.java, id)
        assertThat(status).isEqualTo(PaymentStatus.CREATED.name)

        val eventsCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", Int::class.java, id,
        )
        assertThat(eventsCount).isZero()
    }

}
