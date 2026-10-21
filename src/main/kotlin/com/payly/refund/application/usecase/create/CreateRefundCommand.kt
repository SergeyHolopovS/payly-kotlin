package com.payly.refund.application.usecase.create

import java.util.UUID

data class CreateRefundCommand(
    val merchantId: UUID,
    val paymentId: String,
    val amount: Long? = null,
)
