package com.payly.refund.infrastructure.metrics

import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import com.payly.refund.application.metrics.RefundMetrics
import com.payly.refund.domain.model.Refund
import com.payly.share.infrastructure.metrics.afterCommit
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class MicrometerRefundMetrics(
    private val registry: MeterRegistry,
) : RefundMetrics {

    override fun refundCreated(refund: Refund, payment: Payment) = afterCommit {
        // full — этим возвратом платёж вернули целиком (в т.ч. остаток после частичных)
        val type = if (payment.status == PaymentStatus.REFUNDED) "full" else "partial"

        // Не "payly.refunds.created" — суффикс _created в Prometheus зарезервирован (см. MicrometerPaymentMetrics)
        Counter.builder("payly.refunds.issued")
            .description("Проведённые возвраты")
            .tag("type", type)
            .tag("currency", payment.currency)
            .register(registry)
            .increment()

        DistributionSummary.builder("payly.refunds.amount")
            .description("Суммы возвратов в минимальных единицах валюты (копейки, центы)")
            .tag("currency", payment.currency)
            .register(registry)
            .record(refund.amount.toDouble())
    }

}
