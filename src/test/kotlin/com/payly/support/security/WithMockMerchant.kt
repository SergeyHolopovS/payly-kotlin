package com.payly.support.security

import com.payly.support.data.TestMerchants
import org.springframework.security.test.context.support.WithSecurityContext
import java.lang.annotation.Inherited

/**
 * Подменяет аутентификацию продавца в тестах без похода в БД за API-ключом.
 *
 * Кладёт в SecurityContext тот же Authentication, что и
 * [com.payly.share.infrastructure.security.ApiKeyFilter] (principal — UUID продавца),
 * поэтому @CurrentMerchantId и идемпотентность работают как в проде.
 *
 * Вешается на класс или метод (метод переопределяет класс). Строка продавца
 * в merchants при этом НЕ создаётся — её заводит базовый класс теста
 * (см. [com.payly.support.AbstractIntegrationTest]).
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@MustBeDocumented
@WithSecurityContext(factory = WithMockMerchantSecurityContextFactory::class)
annotation class WithMockMerchant(
    val merchantId: String = TestMerchants.DEFAULT_MERCHANT_ID,
)
