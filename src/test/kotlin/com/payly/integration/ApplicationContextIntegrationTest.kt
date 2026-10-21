package com.payly.integration

import com.payly.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test

/**
 * Smoke-тест: Spring-контекст поднимается на реальной инфраструктуре
 * (миграции Flyway применяются, схема проходит ddl-auto=validate, все бины создаются).
 * Если падает он — остальные интеграционные тесты смотреть бессмысленно.
 */
class ApplicationContextIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun contextLoads() {
    }

}
