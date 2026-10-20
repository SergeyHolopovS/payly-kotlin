package com.payly.payment.domain.model

/**
 * Единое представление платежа для payload'а outbox-событий
 * (PAYMENT_CREATED / PAYMENT_SUCCEEDED / PAYMENT_FAILED).
 */
fun Payment.toEventPayload(): Map<String, String> = mapOf(
    "paymentId" to id,
    "merchantId" to merchantId.toString(),
    "amount" to amount.toString(),
    "currency" to currency,
    "status" to status.name,
)
