package com.payly.support.data

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Сбрасывает состояние между тестами: контейнеры и Spring-контекст общие,
 * поэтому каждый тест сам отвечает за чистую БД и Redis (кэш идемпотентности).
 */
class TestDatabase(
    private val jdbcTemplate: JdbcTemplate,
    private val redisTemplate: StringRedisTemplate,
) {

    fun clean() {
        // Одним TRUNCATE по всем таблицам: так не важен порядок из-за внешних ключей,
        // а CASCADE подстрахует, если появится новая таблица со ссылкой на эти.
        // flyway_schema_history не трогаем — миграции применяются один раз на контейнер
        jdbcTemplate.execute(
            """
            TRUNCATE TABLE
                refunds,
                webhook_deliveries,
                outbox_events,
                idempotency_records,
                payments,
                merchants
            CASCADE
            """.trimIndent()
        )

        // В Redis лежат кэш записей идемпотентности и распределённые локи.
        // Без очистки повтор Idempotency-Key "достал" бы ответ из предыдущего теста
        redisTemplate.execute { connection ->
            connection.serverCommands().flushDb()
        }
    }

}
