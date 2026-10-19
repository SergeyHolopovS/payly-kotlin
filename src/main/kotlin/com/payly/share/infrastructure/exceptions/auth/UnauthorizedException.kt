package com.payly.share.infrastructure.exceptions.auth

import com.payly.share.infrastructure.exceptions.basic.BasicException
import org.springframework.http.HttpStatus

data class UnauthorizedException(
    override val message: String = "Не авторизован",
    override val status: HttpStatus = HttpStatus.UNAUTHORIZED
) : BasicException(message, status)
