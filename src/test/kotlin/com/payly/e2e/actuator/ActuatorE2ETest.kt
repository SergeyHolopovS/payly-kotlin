package com.payly.e2e.actuator

import com.payly.support.AbstractE2ETest
import com.payly.support.data.TestMerchants
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.test.web.servlet.client.RestTestClient

/**
 * E2E-тесты Actuator: эндпоинты доступны на отдельном management-порту без
 * API-ключа, а на порту API их нет — метрики не утекают наружу вместе с API.
 */
class ActuatorE2ETest : AbstractE2ETest() {

    @LocalManagementPort
    private var managementPort: Int = 0

    private val management: RestTestClient by lazy {
        RestTestClient.bindToServer().baseUrl("http://localhost:$managementPort").build()
    }

    private val apiKey = "sk_test_owner"

    @BeforeEach
    fun createMerchant() {
        merchants.create(id = TestMerchants.DEFAULT_MERCHANT_ID, apiKey = apiKey)
    }

    @Test
    fun `liveness и readiness пробы отвечают UP без аутентификации`() {
        management.get().uri("/actuator/health/liveness").exchange()
            .expectStatus().isOk
            .expectBody().jsonPath("$.status").isEqualTo("UP")

        // readiness включает БД и Redis — значит, контейнеры реально проверены
        management.get().uri("/actuator/health/readiness").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.components.db.status").isEqualTo("UP")
            .jsonPath("$.components.redis.status").isEqualTo("UP")
    }

    @Test
    fun `info отдаёт описание приложения и версию сборки`() {
        management.get().uri("/actuator/info").exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.app.name").isEqualTo("payly")
            .jsonPath("$.build.version").exists()
    }

    @Test
    fun `prometheus отдаёт бизнес-метрики после создания платежа`() {
        createPaymentViaApi(apiKey)

        val body = management.get().uri("/actuator/prometheus").exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult().responseBody!!

        assertThat(body)
            .contains("payly_payments_initiated_total{")
            .contains("payly_payments_finalized_total{")
            .contains("payly_payment_gateway_charge_seconds_bucket{")
            // гистограмма HTTP-запросов и общий тег application
            .contains("http_server_requests_seconds_bucket{")
            .contains("application=\"payly\"")
    }

    @Test
    fun `на порту API эндпоинтов Actuator нет`() {
        client.get().uri("/actuator/prometheus").exchange()
            .expectStatus().is4xxClientError
    }

}
