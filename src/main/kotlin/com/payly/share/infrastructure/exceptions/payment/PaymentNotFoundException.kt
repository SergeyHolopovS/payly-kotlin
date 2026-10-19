package com.payly.share.infrastructure.exceptions.payment

import com.payly.share.infrastructure.exceptions.basic.BasicException
import org.springframework.http.HttpStatus

data class PaymentNotFoundException(
    override val message: String = "Платёж не найден",
    override val status: HttpStatus = HttpStatus.NOT_FOUND,
) : BasicException(message, status)
