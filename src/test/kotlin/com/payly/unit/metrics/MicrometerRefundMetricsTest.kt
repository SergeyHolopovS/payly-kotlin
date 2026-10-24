package com.payly.unit.metrics

import com.payly.payment.domain.model.PaymentStatus
import com.payly.refund.infrastructure.metrics.MicrometerRefundMetrics
import com.payly.support.UnitTest
import com.payly.support.fixtures.aPayment
import com.payly.support.fixtures.aRefund
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Unit-тесты [MicrometerRefundMetrics] на SimpleMeterRegistry: тип возврата
 * (полный/частичный), сумма и учёт только после коммита.
 */
@UnitTest
class MicrometerRefundMetricsTest {

    private val registry = SimpleMeterRegistry()

    private val metrics = MicrometerRefundMetrics(registry)

    @AfterEach
    fun clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `возврат, после которого платёж REFUNDED, считается полным`() {
        metrics.refundCreated(aRefund(amount = 10_000), aPayment(status = PaymentStatus.REFUNDED, currency = "RUB"))

        val counter = registry.get("payly.refunds.issued").tags("type", "full", "currency", "RUB").counter()
        assertThat(counter.count()).isEqualTo(1.0)
    }

    @Test
    fun `возврат, после которого платёж PARTIALLY_REFUNDED, считается частичным`() {
        metrics.refundCreated(aRefund(amount = 3_000), aPayment(status = PaymentStatus.PARTIALLY_REFUNDED))

        assertThat(registry.get("payly.refunds.issued").tags("type", "partial").counter().count()).isEqualTo(1.0)
        assertThat(registry.find("payly.refunds.issued").tags("type", "full").counter()).isNull()
    }

    @Test
    fun `сумма возврата пишется в распределение по валюте`() {
        metrics.refundCreated(aRefund(amount = 3_000), aPayment(status = PaymentStatus.PARTIALLY_REFUNDED, currency = "USD"))
        metrics.refundCreated(aRefund(amount = 7_000), aPayment(status = PaymentStatus.REFUNDED, currency = "USD"))

        val summary = registry.get("payly.refunds.amount").tags("currency", "USD").summary()
        assertThat(summary.count()).isEqualTo(2)
        assertThat(summary.totalAmount()).isEqualTo(10_000.0)
    }

    @Test
    fun `внутри транзакции возврат учитывается только после коммита`() {
        TransactionSynchronizationManager.initSynchronization()

        metrics.refundCreated(aRefund(), aPayment(status = PaymentStatus.REFUNDED))

        assertThat(registry.find("payly.refunds.issued").counter()).isNull()

        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

        assertThat(registry.get("payly.refunds.issued").counter().count()).isEqualTo(1.0)
    }

}
