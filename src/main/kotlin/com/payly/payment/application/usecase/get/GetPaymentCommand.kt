package com.payly.payment.application.usecase.get

import java.util.UUID

data class GetPaymentCommand(
    val id: String,
    val merchantId: UUID
)
