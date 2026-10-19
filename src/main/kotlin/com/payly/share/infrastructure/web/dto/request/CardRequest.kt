package com.payly.share.infrastructure.web.dto.request

import com.payly.share.infrastructure.web.validation.NotExpiredCard
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

@NotExpiredCard
data class CardRequest(

    @field:NotBlank(message = "Номер карты обязателен")
    @field:Pattern(regexp = "\\d{12,19}", message = "Номер карты должен содержать от 12 до 19 цифр")
    val number: String,

    @field:NotNull(message = "Месяц окончания действия обязателен")
    @field:Min(1, message = "Месяц должен быть от 1 до 12")
    @field:Max(12, message = "Месяц должен быть от 1 до 12")
    val expMonth: Int,

    @field:NotNull(message = "Год окончания действия обязателен")
    val expYear: Int,

    @field:NotBlank(message = "CVC обязателен")
    @field:Pattern(regexp = "\\d{3}", message = "CVC должен содержать 3 цифры")
    val cvc: String,
)