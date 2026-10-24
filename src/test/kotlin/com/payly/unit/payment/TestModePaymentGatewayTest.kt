package com.payly.unit.payment

import com.payly.payment.domain.gateway.PaymentGatewayResult
import com.payly.payment.domain.model.PaymentDeclineReason
import com.payly.payment.infrastructure.gateway.TestCardOutcome
import com.payly.payment.infrastructure.gateway.TestModePaymentGateway
import com.payly.support.UnitTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit-тесты каталога тестовых карт [TestCardOutcome] и симулятора [TestModePaymentGateway].
 * Карта с долгой обработкой (…3220) здесь не проверяется — она спит 30 секунд.
 */
@UnitTest
class TestModePaymentGatewayTest {

    private val gateway = TestModePaymentGateway()

    @ParameterizedTest
    @CsvSource(
        "4000000000000002, CARD_DECLINED",
        "4000000000009995, INSUFFICIENT_FUNDS",
        "4000000000000069, EXPIRED_CARD",
        "4000000000000119, PROCESSING_ERROR",
    )
    fun `карта из каталога отказов отклоняется с нужной причиной`(card: String, reason: PaymentDeclineReason) {
        assertThat(gateway.charge(card)).isEqualTo(PaymentGatewayResult.Declined(reason))
    }

    @Test
    fun `любая другая карта подтверждается`() {
        assertThat(gateway.charge("4242424242424242")).isEqualTo(PaymentGatewayResult.Approved)
    }

    @Test
    fun `исход определяется только по последним 4 цифрам`() {
        // Тот же исход, что и у полной карты, — на этом держится асинхронный путь, где в БД есть только last4
        assertThat(gateway.charge("5555555555550002"))
            .isEqualTo(PaymentGatewayResult.Declined(PaymentDeclineReason.CARD_DECLINED))
    }

    @Test
    fun `исход по last4 из каталога содержит код причины отказа`() {
        val outcome = TestCardOutcome.resolve("9995")

        assertThat(outcome.isSuccess).isFalse()
        assertThat(outcome.failureReason).isEqualTo("insufficient_funds")
    }

    @Test
    fun `исход по неизвестному last4 — успех без причины`() {
        val outcome = TestCardOutcome.resolve("4242")

        assertThat(outcome.isSuccess).isTrue()
        assertThat(outcome.failureReason).isNull()
    }

}
