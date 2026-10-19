package com.payly.share.infrastructure.exceptions.basic

import org.springframework.http.HttpStatus

open class BasicException(
    override val message: String,
    open val status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
    open val code: String? = null,
) : RuntimeException(message)
