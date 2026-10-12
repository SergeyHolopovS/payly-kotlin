package com.payly.share.exceptions.basic

import org.springframework.http.HttpStatus

open class BasicException(
    override val message: String,
    open val status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
) : RuntimeException(message)
