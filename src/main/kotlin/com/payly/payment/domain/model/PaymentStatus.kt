package com.payly.payment.domain.model

enum class PaymentStatus {
    CREATED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    val isFinal: Boolean
        get() = this == FAILED || this == CANCELED || this == REFUNDED

    fun canTransitionTo(target: PaymentStatus): Boolean = when (this) {
        CREATED -> target == PROCESSING || target == CANCELED
        PROCESSING -> target == SUCCEEDED || target == FAILED || target == CANCELED
        SUCCEEDED -> target == PARTIALLY_REFUNDED || target == REFUNDED
        PARTIALLY_REFUNDED -> target == REFUNDED
        FAILED, CANCELED, REFUNDED -> false
    }
}
