package com.payly.share.security.resolver

import com.payly.share.exceptions.auth.InvalidPrincipalException
import com.payly.share.security.annotation.CurrentMerchantId
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

@Component
class CurrentMerchantIdArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentMerchantId::class.java) && parameter.parameterType == UUID::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw InvalidPrincipalException()

        return try {
            UUID.fromString(authentication.name)
        } catch (_: IllegalArgumentException) {
            throw InvalidPrincipalException()
        }
    }
}
