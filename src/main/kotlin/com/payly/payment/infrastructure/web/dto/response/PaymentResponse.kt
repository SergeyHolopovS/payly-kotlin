package com.payly.payment.infrastructure.web.dto.response

import java.time.Instant

data class PaymentResponse(
    val id: String,
    val amount: Long,
    val currency: String,
    val status: String,
    val description: String?,
    val cardLast4: String,
    val refundedAmount: Long,
    val metadata: Map<String, Any?>,
    val createdAt: Instant,
)
