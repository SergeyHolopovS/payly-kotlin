package com.payly.payment.infrastructure.web.dto.request

import com.payly.share.infrastructure.web.dto.request.CardRequest
import com.payly.share.infrastructure.web.validation.SupportedCurrency
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class CreatePaymentRequest(

    @field:NotNull(message = "Сумма обязательна")
    @field:Positive(message = "Сумма должна быть положительной")
    @field:Max(100_000_000, message = "Сумма не должна превышать 100 000 000")
    val amount: Long,

    @field:NotBlank(message = "Валюта обязательна")
    @field:SupportedCurrency
    val currency: String,

    val description: String? = null,

    @field:NotNull(message = "Данные карты обязательны")
    @field:Valid
    val card: CardRequest,

    val metadata: Map<String, String> = emptyMap(),
)
