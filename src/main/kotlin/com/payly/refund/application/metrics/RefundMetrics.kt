package com.payly.refund.application.metrics

import com.payly.payment.domain.model.Payment
import com.payly.refund.domain.model.Refund

/**
 * Бизнес-метрики возвратов, реализация —
 * [com.payly.refund.infrastructure.metrics.MicrometerRefundMetrics].
 *
 * Вызывать можно внутри транзакции: реализация учтёт событие только после коммита.
 */
interface RefundMetrics {

    /** [payment] — платёж уже после применения возврата (с новым статусом). */
    fun refundCreated(refund: Refund, payment: Payment)

}
