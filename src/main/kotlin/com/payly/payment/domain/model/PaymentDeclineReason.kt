package com.payly.payment.domain.model

enum class PaymentDeclineReason(val code: String) {
    CARD_DECLINED("card_declined"),
    INSUFFICIENT_FUNDS("insufficient_funds"),
    EXPIRED_CARD("expired_card"),
    PROCESSING_ERROR("processing_error"),
}
