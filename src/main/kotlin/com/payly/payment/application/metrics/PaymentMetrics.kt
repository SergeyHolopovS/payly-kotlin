package com.payly.payment.application.metrics

import com.payly.payment.domain.gateway.PaymentGatewayResult
import com.payly.payment.domain.model.Payment
import java.time.Duration

/**
 * Бизнес-метрики платежей. Порт прикладного слоя: use case'ы сообщают о
 * событиях, а как и куда их считать (Micrometer → Prometheus) — решает
 * инфраструктура, см. [com.payly.payment.infrastructure.metrics.MicrometerPaymentMetrics].
 *
 * Вызывать можно внутри транзакции: реализация учтёт событие только после коммита.
 */
interface PaymentMetrics {

    fun paymentCreated(payment: Payment)

    /** [payment] — уже в финальном статусе SUCCEEDED или FAILED. */
    fun paymentFinalized(payment: Payment, path: FinalizationPath)

    fun paymentCanceled(payment: Payment)

    fun gatewayCharged(result: PaymentGatewayResult, duration: Duration)

    /** Гейтвей не ответил вовсе (исключение), а не отклонил платёж. */
    fun gatewayChargeFailed(duration: Duration)

}

/** Кто довёл платёж до финального статуса. */
enum class FinalizationPath {
    // Синхронный ответ гейтвея в запросе создания платежа
    SYNC,
    // PaymentProcessingScheduler — для платежей, застрявших в PROCESSING
    ASYNC,
}
