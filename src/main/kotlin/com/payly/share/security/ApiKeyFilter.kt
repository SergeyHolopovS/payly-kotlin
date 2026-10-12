package com.payly.share.security

import com.payly.merchant.infrastructure.persistence.repository.MerchantJpaRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val BEARER_PREFIX = "Bearer "

@Component
class ApiKeyFilter(
    private val merchantJpaRepository: MerchantJpaRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            val apiKeyHash = header.removePrefix(BEARER_PREFIX)
            val merchant = merchantJpaRepository.findByApiKeyHash(apiKeyHash).orElse(null)

            if (merchant != null) {
                val authentication = UsernamePasswordAuthenticationToken(merchant.id, null, emptyList())
                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }
}
