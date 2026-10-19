package com.payly.payment.application.usecase.create

import java.util.UUID

data class CreatePaymentCommand (
    val merchantId: UUID,
    val amount: Long,
    val currency: String,
    val description: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val cardNumber: String,
)