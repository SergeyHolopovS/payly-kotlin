package com.payly.e2e.payment

import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.infrastructure.web.dto.request.CreatePaymentRequest
import com.payly.payment.infrastructure.web.dto.response.PaymentResponse
import com.payly.support.AbstractE2ETest
import com.payly.support.fixtures.TestCards
import com.payly.support.fixtures.aCardRequest
import com.payly.support.fixtures.aCreatePaymentRequest
import com.payly.support.kafka.KafkaTestConsumer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * E2E-тесты создания платежа глазами клиента API: настоящий HTTP-запрос
 * с API-ключом → ApiKeyFilter → контроллер → TestModePaymentGateway → БД →
 * outbox publisher → Kafka. Ничего не мокается, исход платежа определяется
 * номером тестовой карты (см. [TestCards]).
 *
 * Здесь не дублируются проверки валидации и идемпотентности — они покрыты
 * интеграционными тестами; e2e проверяют, что весь путь собран правильно.
 */
class CreatePaymentE2ETest : AbstractE2ETest() {

    // Топик берём из конфигурации приложения, чтобы не расходиться с OutboxEventPublisher
    @Value("\${outbox.publisher.topic}")
    private lateinit var topic: String

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    // ApiKeyFilter ищет продавца по значению Bearer-токена
    private val apiKey = "sk_test_e2e"

    @BeforeEach
    fun createMerchant() {
        // В e2e продавец заводится с реальным API-ключом, а не мокается
        merchants.create(apiKey = apiKey)
    }

    @Test
    fun `платёж по успешной карте проходит весь путь до события в Kafka`() {
        // Consumer подписывается до создания платежа и читает топик с начала,
        // поэтому событие не потеряется, даже если publisher успеет раньше нас
        KafkaTestConsumer(topic).use { kafka ->
            // 1. Создаём платёж
            val created = createPayment(aCreatePaymentRequest(amount = 25_000))
                .expectStatus().isOk
                .expectBody(PaymentResponse::class.java)
                .returnResult().responseBody!!

            // Гейтвей синхронный, поэтому финальный статус приходит сразу в ответе
            assertThat(created.status).isEqualTo(PaymentStatus.SUCCEEDED.name)
            assertThat(created.amount).isEqualTo(25_000)
            assertThat(created.cardLast4).isEqualTo("4242")

            // 2. Платёж доступен на чтение тому же продавцу
            client.get().uri("/api/v1/payment/{id}", created.id)
                .header("Authorization", "Bearer $apiKey")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(created.id)
                .jsonPath("$.status").isEqualTo(PaymentStatus.SUCCEEDED.name)

            // 3. Outbox publisher доставил PAYMENT_CREATED в Kafka с ключом = id платежа.
            // Publisher работает по расписанию, поэтому ждём событие, а не читаем сразу
            val records = kafka.awaitRecords { it.key() == created.id }
            val event = objectMapper.readTree(records.first().value())
            assertThat(event["eventType"].asString()).isEqualTo(OutboxEventType.PAYMENT_CREATED.name)
            assertThat(event["aggregateId"].asString()).isEqualTo(created.id)
            // В payload'е outbox-события все значения — строки
            assertThat(event["payload"]["amount"].asString()).isEqualTo("25000")
        }
    }

    @Test
    fun `платёж по отклоняемой карте завершается в статусе FAILED`() {
        // Карта ...0002 — TestModePaymentGateway отвечает отказом card_declined
        createPayment(aCreatePaymentRequest(card = aCardRequest(number = TestCards.DECLINED)))
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo(PaymentStatus.FAILED.name)
            .jsonPath("$.cardLast4").isEqualTo("0002")
    }

    @Test
    fun `запрос без API-ключа не проходит аутентификацию`() {
        // Без своего entry point Spring Security отвечает 403, а не 401
        createPayment(aCreatePaymentRequest(), authorization = null)
            .expectStatus().isForbidden

        assertThat(paymentsCount()).isZero()
    }

    @Test
    fun `запрос с неизвестным API-ключом не проходит аутентификацию`() {
        // ApiKeyFilter не нашёл продавца — контекст остаётся пустым, как и без заголовка
        createPayment(aCreatePaymentRequest(), authorization = "Bearer sk_test_unknown")
            .expectStatus().isForbidden

        assertThat(paymentsCount()).isZero()
    }

    /**
     * Отправляет запрос на создание платежа по HTTP. По умолчанию авторизуется
     * ключом продавца из [createMerchant]; `null` — запрос без заголовка Authorization.
     */
    private fun createPayment(
        request: CreatePaymentRequest,
        authorization: String? = "Bearer $apiKey",
    ): RestTestClient.ResponseSpec =
        client.post().uri("/api/v1/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .apply { authorization?.let { header("Authorization", it) } }
            .body(request)
            .exchange()

    private fun paymentsCount(): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM payments", Int::class.java)!!

}
