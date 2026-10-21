package com.payly.refund.infrastructure.web.dto.request

import jakarta.validation.constraints.Positive

data class CreateRefundRequest(

    @field:Positive(message = "Сумма должна быть положительной")
    val amount: Long? = null,

)
