package com.payly.share.infrastructure.exceptions.refund

import com.payly.share.infrastructure.exceptions.basic.BasicException
import org.springframework.http.HttpStatus

data class RefundNotFoundException(
    override val message: String = "Возврат не найден",
    override val status: HttpStatus = HttpStatus.NOT_FOUND,
) : BasicException(message, status)
