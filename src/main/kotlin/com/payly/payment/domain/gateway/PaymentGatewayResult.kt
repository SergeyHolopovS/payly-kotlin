package com.payly.payment.domain.gateway

import com.payly.payment.domain.model.PaymentDeclineReason

sealed interface PaymentGatewayResult {
    data object Approved : PaymentGatewayResult
    data class Declined(val reason: PaymentDeclineReason) : PaymentGatewayResult
}
