package com.payly.refund.infrastructure.web.dto.response

import java.time.Instant

data class RefundResponse(
    val id: String,
    val paymentId: String,
    val amount: Long,
    val status: String,
    val createdAt: Instant,
)
