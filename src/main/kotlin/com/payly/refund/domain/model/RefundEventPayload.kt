package com.payly.refund.domain.model

fun Refund.toEventPayload(): Map<String, String> = mapOf(
    "refundId" to id,
    "paymentId" to paymentId,
    "amount" to amount.toString(),
    "status" to status.name,
)
