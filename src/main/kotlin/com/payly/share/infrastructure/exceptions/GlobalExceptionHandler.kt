package com.payly.share.infrastructure.exceptions

import com.payly.share.infrastructure.exceptions.basic.BasicException
import com.payly.share.infrastructure.exceptions.dto.ErrorDto
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.method.annotation.HandlerMethodValidationException

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BasicException::class)
    fun handleBasicException(ex: BasicException): ResponseEntity<ErrorDto> {
        return ResponseEntity.status(ex.status).body(ErrorDto(message = ex.message, status = ex.status, code = ex.code))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorDto> {
        val status = HttpStatus.BAD_REQUEST
        val message = ex.bindingResult.allErrors.joinToString("; ") { error ->
            when (error) {
                is FieldError -> "${error.field}: ${error.defaultMessage}"
                else -> error.defaultMessage ?: "Ошибка валидации"
            }
        }
        return ResponseEntity.status(status).body(ErrorDto(message = message, status = status))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(ex: ConstraintViolationException): ResponseEntity<ErrorDto> {
        val status = HttpStatus.BAD_REQUEST
        val message = ex.constraintViolations.joinToString("; ") { "${it.propertyPath}: ${it.message}" }
        return ResponseEntity.status(status).body(ErrorDto(message = message, status = status))
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleHandlerMethodValidationException(ex: HandlerMethodValidationException): ResponseEntity<ErrorDto> {
        val status = HttpStatus.BAD_REQUEST
        val message = ex.allErrors.joinToString("; ") { it.defaultMessage ?: "Ошибка валидации" }
        return ResponseEntity.status(status).body(ErrorDto(message = message, status = status))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorDto> {
        val status = HttpStatus.BAD_REQUEST
        return ResponseEntity.status(status).body(ErrorDto(message = "Некорректное тело запроса", status = status))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(
        ex: MissingServletRequestParameterException,
    ): ResponseEntity<ErrorDto> {
        val status = HttpStatus.BAD_REQUEST
        val message = "Отсутствует обязательный параметр '${ex.parameterName}'"
        return ResponseEntity.status(status).body(ErrorDto(message = message, status = status))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorDto> {
        val status = HttpStatus.BAD_REQUEST
        val message = "Параметр '${ex.name}' имеет некорректное значение '${ex.value}'"
        return ResponseEntity.status(status).body(ErrorDto(message = message, status = status))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(ex: Exception): ResponseEntity<ErrorDto> {
        val status = HttpStatus.INTERNAL_SERVER_ERROR
        return ResponseEntity.status(status).body(ErrorDto(message = "Внутренняя ошибка сервера", status = status))
    }
}
