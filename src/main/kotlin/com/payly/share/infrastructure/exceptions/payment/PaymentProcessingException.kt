package com.payly.share.infrastructure.exceptions.payment

import com.payly.share.infrastructure.exceptions.basic.BasicException
import org.springframework.http.HttpStatus

/**
 * Операцию нельзя выполнить, пока платёж обрабатывается гейтвеем (статус PROCESSING):
 * результат списания ещё неизвестен. Клиенту нужно дождаться финального статуса
 * (SUCCEEDED / FAILED) и, если нужно вернуть деньги, сделать возврат.
 */
data class PaymentProcessingException(
    override val message: String = "Платёж обрабатывается — дождитесь результата и при необходимости оформите возврат",
    override val status: HttpStatus = HttpStatus.CONFLICT,
    override val code: String = "payment_processing",
) : BasicException(message, status, code)
