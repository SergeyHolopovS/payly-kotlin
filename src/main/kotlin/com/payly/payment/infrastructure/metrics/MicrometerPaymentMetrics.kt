package com.payly.payment.infrastructure.metrics

import com.payly.payment.application.metrics.FinalizationPath
import com.payly.payment.application.metrics.PaymentMetrics
import com.payly.payment.domain.gateway.PaymentGatewayResult
import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import com.payly.share.infrastructure.metrics.afterCommit
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration

private const val NO_REASON = "none"

/**
 * Значения тегов берутся только из ограниченных множеств (поддерживаемые валюты,
 * enum'ы статусов и причин отказа) — id платежа или продавца в теги не попадают,
 * иначе каждое значение порождало бы новый временной ряд в Prometheus.
 *
 * Имена без суффикса `.created`: `_created` в Prometheus/OpenMetrics зарезервирован
 * под время создания счётчика, и клиент Prometheus молча срезает его из имени
 * (`payly.payments.created` превратился бы в `payly_payments_total`).
 *
 * builder(...).register(registry) на каждый вызов — не утечка: реестр возвращает
 * уже зарегистрированный метр с тем же именем и тегами.
 */
@Component
class MicrometerPaymentMetrics(
    private val registry: MeterRegistry,
) : PaymentMetrics {

    override fun paymentCreated(payment: Payment) = afterCommit {
        Counter.builder("payly.payments.initiated")
            .description("Созданные платежи")
            .tag("currency", payment.currency)
            .register(registry)
            .increment()
    }

    override fun paymentFinalized(payment: Payment, path: FinalizationPath) = afterCommit {
        Counter.builder("payly.payments.finalized")
            .description("Платежи, дошедшие до финального статуса после гейтвея")
            .tag("status", payment.status.name.lowercase())
            .tag("reason", payment.failureReason ?: NO_REASON)
            .tag("path", path.name.lowercase())
            .tag("currency", payment.currency)
            .register(registry)
            .increment()

        if (payment.status == PaymentStatus.SUCCEEDED) {
            // sum — оборот, count — число успешных платежей; суммы разных валют не складываются, поэтому тег currency
            DistributionSummary.builder("payly.payments.succeeded.amount")
                .description("Суммы успешных платежей в минимальных единицах валюты (копейки, центы)")
                .tag("currency", payment.currency)
                .register(registry)
                .record(payment.amount.toDouble())
        }
    }

    override fun paymentCanceled(payment: Payment) = afterCommit {
        Counter.builder("payly.payments.canceled")
            .description("Отменённые платежи")
            .tag("currency", payment.currency)
            .register(registry)
            .increment()
    }

    override fun gatewayCharged(result: PaymentGatewayResult, duration: Duration) = when (result) {
        PaymentGatewayResult.Approved -> recordGatewayCharge("approved", NO_REASON, duration)
        is PaymentGatewayResult.Declined -> recordGatewayCharge("declined", result.reason.code, duration)
    }

    override fun gatewayChargeFailed(duration: Duration) =
        recordGatewayCharge("error", NO_REASON, duration)

    // Без afterCommit: вызов гейтвея идёт вне транзакции и уже состоялся, что бы ни было дальше
    private fun recordGatewayCharge(outcome: String, reason: String, duration: Duration) {
        Timer.builder("payly.payment.gateway.charge")
            .description("Время ответа платёжного гейтвея на списание")
            .tag("outcome", outcome)
            .tag("reason", reason)
            .register(registry)
            .record(duration)
    }

}
