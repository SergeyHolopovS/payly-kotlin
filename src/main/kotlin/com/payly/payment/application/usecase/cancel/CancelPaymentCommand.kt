package com.payly.payment.application.usecase.cancel

import java.util.UUID

data class CancelPaymentCommand(
    val merchantId: UUID,
    val id: String
)
