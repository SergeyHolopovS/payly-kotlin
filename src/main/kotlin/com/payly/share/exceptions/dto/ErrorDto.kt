package com.payly.share.exceptions.dto

import org.springframework.http.HttpStatus

data class ErrorDto(
    val message: String = "Внутренняя ошибка сервера",
    val status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
)
