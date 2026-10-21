package com.payly.refund.domain.model

import java.time.Instant

data class Refund(
    val id: String,
    val paymentId: String,
    val amount: Long,
    val status: RefundStatus,
    val createdAt: Instant,
)
