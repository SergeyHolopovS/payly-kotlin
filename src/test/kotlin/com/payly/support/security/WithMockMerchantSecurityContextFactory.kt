package com.payly.support.security

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithSecurityContextFactory
import java.util.UUID

/**
 * Строит SecurityContext для [WithMockMerchant]. Вызывается Spring Security Test
 * перед каждым тестовым методом (раньше, чем @BeforeEach), после теста контекст
 * очищается — между тестами аутентификация не протекает.
 */
class WithMockMerchantSecurityContextFactory : WithSecurityContextFactory<WithMockMerchant> {

    override fun createSecurityContext(annotation: WithMockMerchant): SecurityContext {
        // Аннотации не умеют хранить UUID, поэтому id передаётся строкой и парсится здесь —
        // опечатка в id сразу уронит тест, а не превратится в "чужого" продавца
        val merchantId = UUID.fromString(annotation.merchantId)

        return SecurityContextHolder.createEmptyContext().apply {
            // Тот же вид токена, что создаёт ApiKeyFilter: principal — UUID продавца,
            // без credentials и ролей. Конструктор с authorities помечает токен
            // как аутентифицированный, поэтому anyRequest().authenticated() пропускает запрос
            authentication = UsernamePasswordAuthenticationToken(merchantId, null, emptyList())
        }
    }

}
