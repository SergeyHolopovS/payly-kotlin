package com.payly.share.infrastructure.exceptions.dto

import org.springframework.http.HttpStatus
import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorDto(
    val message: String = "Внутренняя ошибка сервера",
    val status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
    val code: String? = null,
)
