package com.payly.webhook.infrastructure.persistense.entity

enum class DeliveryStatus {
    PENDING,
    DELIVERED,
    FAILED,
    EXHAUSTED,
}