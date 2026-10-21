package com.payly.outbox.domain.model

enum class OutboxEventType {
    PAYMENT_CREATED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    PAYMENT_CANCELLED,
    REFUND_SUCCEEDED,
}
