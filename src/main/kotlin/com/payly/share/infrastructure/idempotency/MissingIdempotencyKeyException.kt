package com.payly.share.infrastructure.idempotency

import com.payly.share.infrastructure.exceptions.basic.BasicException
import org.springframework.http.HttpStatus

data class MissingIdempotencyKeyException(
    override val message: String = "Отсутствует заголовок Idempotency-Key",
    override val status: HttpStatus = HttpStatus.BAD_REQUEST,
    override val code: String = "idempotency_key_missing",
) : BasicException(message, status, code)
