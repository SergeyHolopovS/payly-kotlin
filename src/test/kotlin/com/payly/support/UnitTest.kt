package com.payly.support

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

/**
 * Unit-тест: без Spring-контекста и контейнеров, зависимости — Mockito-моки.
 * Strict stubs роняют тест на неиспользованных стабах, чтобы они не копились.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
// Тег, по которому фильтрует Gradle-задача unitTest
@Tag("unit")
// Создаёт поля с @Mock перед каждым тестом и проверяет стабы после
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
annotation class UnitTest
