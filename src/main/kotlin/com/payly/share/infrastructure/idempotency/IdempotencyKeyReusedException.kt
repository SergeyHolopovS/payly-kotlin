package com.payly.share.infrastructure.idempotency

import com.payly.share.infrastructure.exceptions.basic.BasicException
import org.springframework.http.HttpStatus

data class IdempotencyKeyReusedException(
    override val message: String = "Idempotency-Key уже использован с другим телом запроса",
    override val status: HttpStatus = HttpStatus.UNPROCESSABLE_ENTITY,
    override val code: String = "idempotency_key_reused",
) : BasicException(message, status, code)
