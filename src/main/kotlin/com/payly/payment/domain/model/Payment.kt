package com.payly.payment.domain.model

import java.time.Instant
import java.util.UUID

data class Payment(
    val id: String,
    val merchantId: UUID,
    val amount: Long,
    val currency: String,
    val status: String,
    val description: String?,
    val metadata: Map<String, Any?>,
    val cardLast4: String,
    val failureReason: String?,
    val refundedAmount: Long,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)