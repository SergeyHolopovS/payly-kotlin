package com.payly.share.infrastructure.exceptions.refund

import com.payly.share.infrastructure.exceptions.basic.BasicException
import org.springframework.http.HttpStatus

data class InvalidRefundAmountException(
    override val message: String = "Неправильная сумма возврата",
    override val status: HttpStatus = HttpStatus.BAD_REQUEST,
) : BasicException(message, status)
