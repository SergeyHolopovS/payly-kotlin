package com.payly.outbox.domain.model

enum class OutboxEventType {
    PAYMENT_CREATED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
}
