package com.payly.share.infrastructure.idempotency

import com.payly.share.infrastructure.exceptions.auth.InvalidPrincipalException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.util.UUID

private const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"

@Aspect
@Component
class IdempotencyAspect(
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper,
) {

    @Around("@annotation(Idempotent)")
    fun aroundIdempotentMethod(joinPoint: ProceedingJoinPoint): Any? {
        val key = currentHttpRequest().getHeader(IDEMPOTENCY_KEY_HEADER)
            ?: throw MissingIdempotencyKeyException()

        val requestHash = sha256(objectMapper.writeValueAsString(requestBodyArgument(joinPoint)))

        return idempotencyService.execute(currentMerchantId(), key, requestHash) {
            joinPoint.proceed() as ResponseEntity<*>
        }
    }

    private fun currentHttpRequest() =
        (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request

    private fun currentMerchantId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw InvalidPrincipalException()

        return try {
            UUID.fromString(authentication.name)
        } catch (_: IllegalArgumentException) {
            throw InvalidPrincipalException()
        }
    }

    private fun requestBodyArgument(joinPoint: ProceedingJoinPoint): Any? {
        val method = (joinPoint.signature as MethodSignature).method
        val index = method.parameters.indexOfFirst { it.isAnnotationPresent(RequestBody::class.java) }
        return if (index >= 0) joinPoint.args[index] else null
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

}
