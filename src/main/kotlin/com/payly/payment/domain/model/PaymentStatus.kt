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
        // PROCESSING означает, что запрос уже ушёл в гейтвей и деньги могут быть списаны,
        // поэтому отменить такой платёж нельзя — только дождаться результата
        // и, если он успешный, сделать возврат. Отмена возможна только из CREATED
        PROCESSING -> target == SUCCEEDED || target == FAILED
        SUCCEEDED -> target == PARTIALLY_REFUNDED || target == REFUNDED
        PARTIALLY_REFUNDED -> target == REFUNDED
        FAILED, CANCELED, REFUNDED -> false
    }
}
