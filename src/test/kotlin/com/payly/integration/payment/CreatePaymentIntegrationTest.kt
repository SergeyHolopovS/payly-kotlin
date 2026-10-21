package com.payly.integration.payment

import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.gateway.PaymentGateway
import com.payly.payment.domain.gateway.PaymentGatewayResult
import com.payly.payment.domain.model.PaymentDeclineReason
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.infrastructure.web.dto.request.CreatePaymentRequest
import com.payly.payment.infrastructure.web.dto.response.PaymentResponse
import com.payly.support.AbstractIntegrationTest
import com.payly.support.data.TestMerchants
import com.payly.support.fixtures.TestCards
import com.payly.support.fixtures.aCardRequest
import com.payly.support.fixtures.aCreatePaymentRequest
import com.payly.support.security.WithMockMerchant
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Интеграционные тесты `POST /api/v1/payment`: контроллер, валидация,
 * идемпотентность, use case и реальная БД работают вместе.
 *
 * Подменены только границы системы:
 *  - аутентификация — через [WithMockMerchant] (без API-ключа в заголовке);
 *  - платёжный гейтвей — через @MockitoBean, чтобы управлять исходом платежа
 *    и проверять, был ли поход в гейтвей вообще.
 */
@WithMockMerchant
class CreatePaymentIntegrationTest : AbstractIntegrationTest() {

    // Заменяет бин TestModePaymentGateway в контексте на Mockito-мок.
    // Мок сбрасывается после каждого теста, поэтому стабы не протекают между тестами
    @MockitoBean
    private lateinit var paymentGateway: PaymentGateway

    @BeforeEach
    fun approveByDefault() {
        // По умолчанию гейтвей одобряет любую карту; тесты на отказ переопределяют это
        whenever(paymentGateway.charge(any())).thenReturn(PaymentGatewayResult.Approved)
    }

    // ---------- Успешное создание ----------

    @Test
    fun `создаёт успешный платёж и сохраняет его вместе с outbox-событием`() {
        // Проверяем контракт ответа API
        val response = createPayment(aCreatePaymentRequest(amount = 15_000, currency = "USD"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amount").value(15_000))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.status").value(PaymentStatus.SUCCEEDED.name))
            .andExpect(jsonPath("$.cardLast4").value("4242"))
            .andExpect(jsonPath("$.refundedAmount").value(0))
            .andExpect(jsonPath("$.metadata.orderId").value("42"))
            .readBody<PaymentResponse>()

        // Проверяем, что в БД лежит то же самое: платёж привязан к продавцу
        // из @WithMockMerchant и хранит только последние 4 цифры карты
        val row = jdbcTemplate.queryForMap("SELECT * FROM payments WHERE id = ?", response.id)
        assertThat(row["merchant_id"]).isEqualTo(UUID.fromString(TestMerchants.DEFAULT_MERCHANT_ID))
        assertThat(row["status"]).isEqualTo(PaymentStatus.SUCCEEDED.name)
        assertThat(row["card_last4"]).isEqualTo("4242")
        assertThat(row["failure_reason"]).isNull()

        // В outbox ровно одно событие по этому платежу
        val eventTypes = jdbcTemplate.queryForList(
            "SELECT event_type FROM outbox_events WHERE aggregate_id = ?", String::class.java, response.id,
        )
        assertThat(eventTypes).containsExactly(OutboxEventType.PAYMENT_CREATED.name)

        // В гейтвей ушёл полный номер карты из запроса
        verify(paymentGateway).charge(TestCards.SUCCESS)
    }

    @Test
    fun `отклонённый гейтвеем платёж сохраняется в статусе FAILED с причиной`() {
        whenever(paymentGateway.charge(any()))
            .thenReturn(PaymentGatewayResult.Declined(PaymentDeclineReason.INSUFFICIENT_FUNDS))

        // Отказ гейтвея — это не ошибка API: платёж создан, просто неуспешный
        val response = createPayment(aCreatePaymentRequest())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value(PaymentStatus.FAILED.name))
            .readBody<PaymentResponse>()

        // failureReason не отдаётся в API, поэтому проверяем его в БД
        val row = jdbcTemplate.queryForMap("SELECT status, failure_reason FROM payments WHERE id = ?", response.id)
        assertThat(row["status"]).isEqualTo(PaymentStatus.FAILED.name)
        assertThat(row["failure_reason"]).isEqualTo(PaymentDeclineReason.INSUFFICIENT_FUNDS.code)
    }

    @Test
    // Аннотация на методе переопределяет аннотацию на классе
    @WithMockMerchant(merchantId = TestMerchants.OTHER_MERCHANT_ID)
    fun `платёж привязывается к аутентифицированному продавцу`() {
        // Базовый класс заводит только продавца по умолчанию — второго создаём сами,
        // иначе вставка платежа упадёт на внешнем ключе payments.merchant_id
        merchants.create(id = TestMerchants.OTHER_MERCHANT_ID)

        val response = createPayment(aCreatePaymentRequest())
            .andExpect(status().isOk)
            .readBody<PaymentResponse>()

        val merchantId = jdbcTemplate.queryForObject(
            "SELECT merchant_id FROM payments WHERE id = ?", UUID::class.java, response.id,
        )
        assertThat(merchantId).isEqualTo(UUID.fromString(TestMerchants.OTHER_MERCHANT_ID))
    }

    // ---------- Валидация запроса ----------
    // Во всех случаях запрос отсекается на @Valid в контроллере: в БД ничего
    // не появляется, а гейтвей не вызывается

    @Test
    fun `номер карты, не прошедший проверку Луна, отклоняется до похода в гейтвей`() {
        createPayment(aCreatePaymentRequest(card = aCardRequest(number = TestCards.INVALID_LUHN)))
            .andExpect(status().isBadRequest)
            // GlobalExceptionHandler пишет в message путь до невалидного поля
            .andExpect(jsonPath("$.message", containsString("card.number")))

        verify(paymentGateway, never()).charge(any())
        assertThat(paymentsCount()).isZero()
    }

    @Test
    fun `неподдерживаемая валюта отклоняется`() {
        // Список валют задаётся в payment.supported-currencies (по умолчанию RUB, USD, EUR)
        createPayment(aCreatePaymentRequest(currency = "JPY"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("currency")))

        assertThat(paymentsCount()).isZero()
    }

    @Test
    fun `неположительная сумма отклоняется`() {
        createPayment(aCreatePaymentRequest(amount = 0))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message", containsString("amount")))

        assertThat(paymentsCount()).isZero()
    }

    @Test
    fun `карта с истёкшим сроком действия отклоняется`() {
        // @NotExpiredCard — валидатор уровня класса CardRequest, а не отдельного поля
        createPayment(aCreatePaymentRequest(card = aCardRequest(expMonth = 1, expYear = 2020)))
            .andExpect(status().isBadRequest)

        assertThat(paymentsCount()).isZero()
    }

    // ---------- Идемпотентность ----------

    @Test
    fun `запрос без Idempotency-Key отклоняется`() {
        // Заголовок проверяет IdempotencyAspect ещё до вызова метода контроллера
        createPayment(aCreatePaymentRequest(), idempotencyKey = null)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("idempotency_key_missing"))

        verify(paymentGateway, never()).charge(any())
        assertThat(paymentsCount()).isZero()
    }

    @Test
    fun `повтор с тем же Idempotency-Key возвращает сохранённый ответ без повторного списания`() {
        val request = aCreatePaymentRequest()
        val key = UUID.randomUUID().toString()

        // Первый запрос обрабатывается по-настоящему
        val first = createPayment(request, key)
            .andExpect(status().isOk)
            .andExpect(header().doesNotExist("Idempotent-Replayed"))
            .readBody<PaymentResponse>()

        // Второй с тем же ключом и телом отдаётся из сохранённой записи (Redis/Postgres)
        createPayment(request, key)
            .andExpect(status().isOk)
            .andExpect(header().string("Idempotent-Replayed", "true"))
            .andExpect(jsonPath("$.id").value(first.id))

        // Деньги списаны один раз, платёж в БД один
        verify(paymentGateway, times(1)).charge(any())
        assertThat(paymentsCount()).isEqualTo(1)
    }

    @Test
    fun `тот же Idempotency-Key с другим телом запроса отклоняется`() {
        val key = UUID.randomUUID().toString()

        createPayment(aCreatePaymentRequest(amount = 10_000), key)
            .andExpect(status().isOk)

        // Ключ уже привязан к хэшу другого тела — это ошибка клиента, а не повтор
        createPayment(aCreatePaymentRequest(amount = 20_000), key)
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.code").value("idempotency_key_reused"))

        assertThat(paymentsCount()).isEqualTo(1)
    }

    // ---------- Хелперы ----------

    /**
     * Отправляет запрос на создание платежа. По умолчанию с новым случайным
     * Idempotency-Key, чтобы тесты не пересекались по идемпотентности;
     * `null` — отправить запрос вовсе без заголовка.
     */
    private fun createPayment(
        request: CreatePaymentRequest,
        idempotencyKey: String? = UUID.randomUUID().toString(),
    ): ResultActions =
        mockMvc.perform(
            post("/api/v1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))
                .apply { idempotencyKey?.let { header("Idempotency-Key", it) } }
        )

    /** Десериализует тело ответа MockMvc в DTO. */
    private inline fun <reified T : Any> ResultActions.readBody(): T =
        objectMapper.readValue(andReturn().response.contentAsString, T::class.java)

    private fun paymentsCount(): Int =
        jdbcTemplate.queryForObject("SELECT count(*) FROM payments", Int::class.java)!!

}
