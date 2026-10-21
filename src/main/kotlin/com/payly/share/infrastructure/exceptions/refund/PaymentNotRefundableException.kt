package com.payly.share.infrastructure.exceptions.refund

import com.payly.payment.domain.model.PaymentStatus
import com.payly.share.infrastructure.exceptions.basic.BasicException
import org.springframework.http.HttpStatus

data class PaymentNotRefundableException(
    val paymentStatus: PaymentStatus,
    override val message: String = "Платёж в статусе $paymentStatus не может быть возвращён",
    override val status: HttpStatus = HttpStatus.CONFLICT,
) : BasicException(message, status)
