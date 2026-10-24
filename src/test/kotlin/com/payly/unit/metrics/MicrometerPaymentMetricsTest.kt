package com.payly.unit.metrics

import com.payly.payment.application.metrics.FinalizationPath
import com.payly.payment.domain.gateway.PaymentGatewayResult
import com.payly.payment.domain.model.PaymentDeclineReason
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.infrastructure.metrics.MicrometerPaymentMetrics
import com.payly.support.UnitTest
import com.payly.support.fixtures.aPayment
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Unit-тесты [MicrometerPaymentMetrics] на SimpleMeterRegistry: какие метры
 * с какими тегами появляются и что внутри транзакции они учитываются только после коммита.
 */
@UnitTest
class MicrometerPaymentMetricsTest {

    private val registry = SimpleMeterRegistry()

    private val metrics = MicrometerPaymentMetrics(registry)

    @AfterEach
    fun clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `успешная финализация считается с тегами статуса и пути и пишет сумму`() {
        val payment = aPayment(status = PaymentStatus.SUCCEEDED, amount = 10_000, currency = "RUB")

        metrics.paymentFinalized(payment, FinalizationPath.SYNC)

        val counter = registry.get("payly.payments.finalized")
            .tags("status", "succeeded", "reason", "none", "path", "sync", "currency", "RUB")
            .counter()
        assertThat(counter.count()).isEqualTo(1.0)

        val amount = registry.get("payly.payments.succeeded.amount").tags("currency", "RUB").summary()
        assertThat(amount.totalAmount()).isEqualTo(10_000.0)
    }

    @Test
    fun `проваленный платёж помечается кодом причины и не попадает в оборот`() {
        val payment = aPayment(status = PaymentStatus.FAILED, failureReason = "insufficient_funds")

        metrics.paymentFinalized(payment, FinalizationPath.ASYNC)

        val counter = registry.get("payly.payments.finalized")
            .tags("status", "failed", "reason", "insufficient_funds", "path", "async")
            .counter()
        assertThat(counter.count()).isEqualTo(1.0)
        assertThat(registry.find("payly.payments.succeeded.amount").summary()).isNull()
    }

    @Test
    fun `время гейтвея пишется в таймер с исходом и причиной отказа`() {
        metrics.gatewayCharged(PaymentGatewayResult.Declined(PaymentDeclineReason.CARD_DECLINED), Duration.ofMillis(120))
        metrics.gatewayChargeFailed(Duration.ofSeconds(5))

        val declined = registry.get("payly.payment.gateway.charge")
            .tags("outcome", "declined", "reason", "card_declined")
            .timer()
        assertThat(declined.count()).isEqualTo(1)
        assertThat(declined.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(120.0)

        assertThat(registry.get("payly.payment.gateway.charge").tags("outcome", "error").timer().count()).isEqualTo(1)
    }

    @Test
    fun `внутри транзакции событие учитывается только после коммита`() {
        TransactionSynchronizationManager.initSynchronization()

        metrics.paymentCanceled(aPayment(status = PaymentStatus.CANCELED))

        // До коммита метра нет вовсе — при откате он так и не появится
        assertThat(registry.find("payly.payments.canceled").counter()).isNull()

        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

        assertThat(registry.get("payly.payments.canceled").counter().count()).isEqualTo(1.0)
    }

}
