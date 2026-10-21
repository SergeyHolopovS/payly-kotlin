package com.payly.support

import com.payly.support.data.MerchantTestData
import com.payly.support.data.PaymentTestData
import com.payly.support.data.TestDatabase
import com.payly.support.data.TestMerchants
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.ObjectMapper

/**
 * Интеграционный тест: полный Spring-контекст на реальных Postgres/Redis/Kafka
 * (Testcontainers), запросы через MockMvc без поднятия HTTP-сервера.
 *
 * Аутентификация подменяется через [com.payly.support.security.WithMockMerchant],
 * внешние системы (платёжный гейтвей) — через @MockitoBean в наследниках.
 * Перед каждым тестом БД и Redis очищаются, а продавец по умолчанию заводится заново.
 */
// Тег, по которому фильтрует Gradle-задача integrationTest
@Tag("integration")
@SpringBootTest
// MockMvc с уже подключённым Spring Security — поэтому @WithMockMerchant работает
@AutoConfigureMockMvc
// Подключает Postgres/Redis/Kafka из Testcontainers через @ServiceConnection
@Import(TestcontainersConfiguration::class)
abstract class AbstractIntegrationTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @Autowired
    protected lateinit var objectMapper: ObjectMapper

    // Для подготовки данных и проверок напрямую в БД, в обход репозиториев приложения
    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    protected val merchants: MerchantTestData by lazy { MerchantTestData(jdbcTemplate) }

    protected val payments: PaymentTestData by lazy { PaymentTestData(jdbcTemplate) }

    // Очистка ДО теста, а не после: если тест упал на середине, следующий
    // всё равно стартует с чистой БД, а после падения можно посмотреть, что осталось
    @BeforeEach
    fun resetState() {
        TestDatabase(jdbcTemplate, redisTemplate).clean()
        // Продавец, под которым по умолчанию "логинится" @WithMockMerchant
        merchants.create(id = TestMerchants.DEFAULT_MERCHANT_ID)
    }

    /** Сериализует DTO запроса тем же ObjectMapper, что и приложение. */
    protected fun toJson(value: Any): String = objectMapper.writeValueAsString(value)

}
