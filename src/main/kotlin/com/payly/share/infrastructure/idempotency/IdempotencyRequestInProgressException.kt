package com.payly.share.infrastructure.idempotency

import com.payly.share.infrastructure.exceptions.basic.BasicException
import org.springframework.http.HttpStatus

data class IdempotencyRequestInProgressException(
    override val message: String = "Запрос с этим Idempotency-Key уже обрабатывается",
    override val status: HttpStatus = HttpStatus.CONFLICT,
    override val code: String = "idempotency_request_in_progress",
) : BasicException(message, status, code)
