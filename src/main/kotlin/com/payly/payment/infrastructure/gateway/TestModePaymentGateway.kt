package com.payly.payment.infrastructure.gateway

import com.payly.payment.domain.gateway.PaymentGateway
import com.payly.payment.domain.gateway.PaymentGatewayResult
import org.springframework.stereotype.Component

/**
 * Симулятор платёжного гейтвея для тестового режима (аналог тестовых карт
 * Stripe) — по номеру карты имитирует подтверждение, конкретный отказ или
 * долгую обработку. В проде на этом месте будет интеграция с реальным
 * эквайером.
 *
 * Исход (успех/конкретный отказ) резолвится через [TestCardOutcome] —
 * тот же каталог, которым пользуется и асинхронный планировщик
 * [com.payly.payment.infrastructure.scheduler.PaymentProcessingScheduler],
 * чтобы номера тестовых карт не дублировались в двух местах.
 */
@Component
class TestModePaymentGateway : PaymentGateway {

    override fun charge(cardNumber: String): PaymentGatewayResult {
        val last4 = cardNumber.takeLast(4)

        if (last4 == CARD_SLOW_PROCESSING_LAST4) {
            Thread.sleep(SLOW_PROCESSING_DELAY_MS)
        }

        val outcome = TestCardOutcome.resolve(last4)
        return outcome.declineReason
            ?.let { PaymentGatewayResult.Declined(it) }
            ?: PaymentGatewayResult.Approved
    }

    companion object {
        private const val CARD_SLOW_PROCESSING_LAST4 = "3220"
        private const val SLOW_PROCESSING_DELAY_MS = 30_000L
    }

}
