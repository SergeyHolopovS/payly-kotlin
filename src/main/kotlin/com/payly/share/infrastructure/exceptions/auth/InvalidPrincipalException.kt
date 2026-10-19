package com.payly.share.infrastructure.exceptions.auth

import com.payly.share.infrastructure.exceptions.basic.BasicException
import org.springframework.http.HttpStatus

data class InvalidPrincipalException(
    override val message: String = "Не удалось определить идентификатор продавца",
    override val status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
) : BasicException(message, status)
