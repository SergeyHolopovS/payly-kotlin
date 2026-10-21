package com.payly.support

import com.payly.payment.infrastructure.web.dto.request.CreatePaymentRequest
import com.payly.payment.infrastructure.web.dto.response.PaymentResponse
import com.payly.support.data.MerchantTestData
import com.payly.support.data.PaymentTestData
import com.payly.support.data.TestDatabase
import com.payly.support.fixtures.aCreatePaymentRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.client.RestTestClient
import java.util.UUID

/**
 * E2E-тест: приложение поднимается целиком на случайном порту, запросы идут
 * по настоящему HTTP. Ничего не мокается — ни аутентификация (реальный API-ключ
 * через ApiKeyFilter), ни гейтвей (TestModePaymentGateway), ни Kafka.
 *
 * Outbox publisher опрашивает таблицу чаще, чтобы события быстро доходили до Kafka.
 */
// Тег, по которому фильтрует Gradle-задача e2eTest
@Tag("e2e")
@SpringBootTest(
    // Настоящий Tomcat на свободном порту — запросы проходят весь servlet-стек
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["outbox.publisher.poll-interval-ms=200"],
)
// RestTestClient, уже направленный на порт поднятого сервера
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration::class)
abstract class AbstractE2ETest {

    @Autowired
    protected lateinit var client: RestTestClient

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    protected val merchants: MerchantTestData by lazy { MerchantTestData(jdbcTemplate) }

    protected val payments: PaymentTestData by lazy { PaymentTestData(jdbcTemplate) }

    // В отличие от интеграционных тестов продавец здесь не создаётся заранее:
    // каждому e2e-тесту нужен свой набор (API-ключ, webhook_url), его заводит сам тест
    @BeforeEach
    fun resetState() {
        TestDatabase(jdbcTemplate, redisTemplate).clean()
    }

    /**
     * Создаёт платёж через настоящий API от имени продавца с [apiKey] и возвращает ответ.
     * Для тестов, которым созданный платёж нужен как исходные данные, а не как предмет проверки.
     */
    protected fun createPaymentViaApi(
        apiKey: String,
        request: CreatePaymentRequest = aCreatePaymentRequest(),
    ): PaymentResponse =
        client.post().uri("/api/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $apiKey")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .body(request)
            .exchange()
            .expectStatus().isOk
            .expectBody(PaymentResponse::class.java)
            .returnResult().responseBody!!

}
