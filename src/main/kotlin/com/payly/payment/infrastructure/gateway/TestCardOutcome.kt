package com.payly.payment.infrastructure.gateway

import com.payly.payment.domain.model.PaymentDeclineReason

/**
 * Исход обработки тестовой карты, определяемый по последним 4 цифрам —
 * это единственное, что в итоге сохраняется в БД
 * (см. [com.payly.payment.domain.model.Payment.cardLast4]), поэтому
 * и [TestModePaymentGateway] (синхронный путь), и планировщик
 * [com.payly.payment.infrastructure.scheduler.PaymentProcessingScheduler]
 * (асинхронный путь) резолвят исход через один и тот же каталог.
 */
data class TestCardOutcome(
    val declineReason: PaymentDeclineReason?,
) {
    val isSuccess: Boolean get() = declineReason == null
    val failureReason: String? get() = declineReason?.code

    companion object {
        private val DECLINE_REASONS_BY_LAST4: Map<String, PaymentDeclineReason> = mapOf(
            "0002" to PaymentDeclineReason.CARD_DECLINED,
            "9995" to PaymentDeclineReason.INSUFFICIENT_FUNDS,
            "0069" to PaymentDeclineReason.EXPIRED_CARD,
            "0119" to PaymentDeclineReason.PROCESSING_ERROR,
        )

        fun resolve(cardLast4: String): TestCardOutcome =
            TestCardOutcome(declineReason = DECLINE_REASONS_BY_LAST4[cardLast4])
    }
}
