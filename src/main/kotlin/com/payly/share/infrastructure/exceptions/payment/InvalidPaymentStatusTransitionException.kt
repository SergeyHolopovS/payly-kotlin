package com.payly.share.infrastructure.exceptions.payment

import com.payly.payment.domain.model.PaymentStatus
import com.payly.share.infrastructure.exceptions.basic.BasicException
import org.springframework.http.HttpStatus

data class InvalidPaymentStatusTransitionException(
    val from: PaymentStatus,
    val to: PaymentStatus,
    override val message: String = "Невозможно перевести платёж из статуса $from в статус $to",
    override val status: HttpStatus = HttpStatus.CONFLICT,
) : BasicException(message, status)
