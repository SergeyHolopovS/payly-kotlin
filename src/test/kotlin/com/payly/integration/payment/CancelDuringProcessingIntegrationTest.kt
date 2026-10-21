package com.payly.integration.payment

import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.gateway.PaymentGateway
import com.payly.payment.domain.gateway.PaymentGatewayResult
import com.payly.payment.domain.model.PaymentStatus
import com.payly.support.AbstractIntegrationTest
import com.payly.support.fixtures.aCreatePaymentRequest
import com.payly.support.security.WithMockMerchant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Регрессионный тест гонки "отмена во время похода в гейтвей".
 *
 * CreatePaymentUseCase вызывает гейтвей между двумя транзакциями: платёж уже
 * закоммичен в PROCESSING, а финализация ещё не началась. Раньше отмена в этот
 * момент успевала перевести платёж в CANCELED, после чего финализация падала
 * на optimistic lock и POST отвечал 500 — хотя деньги в гейтвее уже списаны.
 *
 * Теперь платёж в PROCESSING отменить нельзя (409), а создание доходит до конца.
 *
 * Гонка воспроизводится детерминированно: мок гейтвея "зависает" на защёлке,
 * пока тест не выполнит отмену, — без sleep и без зависимости от таймингов.
 */
@WithMockMerchant
class CancelDuringProcessingIntegrationTest : AbstractIntegrationTest() {

    // Тот же набор @MockitoBean, что в CreatePaymentIntegrationTest, — Spring
    // переиспользует закэшированный контекст, а не собирает новый
    @MockitoBean
    private lateinit var paymentGateway: PaymentGateway

    // Отдельный поток для "зависшего" запроса создания, пока основной поток теста отменяет
    private val executor = Executors.newSingleThreadExecutor()

    @AfterEach
    fun shutdownExecutor() {
        executor.shutdownNow()
    }

    @Test
    fun `платёж нельзя отменить, пока гейтвей его обрабатывает, и создание завершается успешно`() {
        val gatewayEntered = CountDownLatch(1)
        val gatewayRelease = CountDownLatch(1)

        // Гейтвей сообщает, что запрос до него дошёл, и ждёт команды ответить.
        // К этому моменту первая транзакция создания уже закоммичена
        whenever(paymentGateway.charge(any())).thenAnswer {
            gatewayEntered.countDown()
            check(gatewayRelease.await(10, TimeUnit.SECONDS)) { "тест не отпустил гейтвей" }
            PaymentGatewayResult.Approved
        }

        // SecurityContext от @WithMockMerchant живёт в ThreadLocal потока теста —
        // передаём его в фоновый запрос явно, иначе тот уйдёт без аутентификации
        val merchantContext = TestSecurityContextHolder.getContext()

        // 1. Создание платежа уходит в фон и застревает в гейтвее
        val creation = executor.submit<String> {
            mockMvc.perform(
                post("/api/v1/payment")
                    .with(securityContext(merchantContext))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .content(toJson(aCreatePaymentRequest()))
            ).andReturn().response.let { "${it.status} ${it.contentAsString}" }
        }
        assertThat(gatewayEntered.await(10, TimeUnit.SECONDS))
            .withFailMessage { "Запрос создания не дошёл до гейтвея: ${if (creation.isDone) creation.get() else "ещё выполняется"}" }
            .isTrue()

        // 2. Платёж виден в БД в PROCESSING — именно в этом окне раньше проходила отмена
        val id = jdbcTemplate.queryForObject("SELECT id FROM payments", String::class.java)!!
        assertThat(statusInDb(id)).isEqualTo(PaymentStatus.PROCESSING.name)

        // 3. Отмена отклоняется с понятным клиенту кодом: подождать результат и сделать возврат
        mockMvc.perform(
            post("/api/v1/payment/{id}/cancel", id)
                .header("Idempotency-Key", UUID.randomUUID().toString())
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("payment_processing"))

        // 4. Гейтвей отвечает — создание должно завершиться успехом, а не 500
        gatewayRelease.countDown()
        val creationResult = creation.get(10, TimeUnit.SECONDS)
        assertThat(creationResult).startsWith("200")
        assertThat(statusInDb(id)).isEqualTo(PaymentStatus.SUCCEEDED.name)

        // В outbox только создание — события об отмене, которой не было, нет
        val eventTypes = jdbcTemplate.queryForList(
            "SELECT event_type FROM outbox_events WHERE aggregate_id = ?", String::class.java, id,
        )
        assertThat(eventTypes).containsExactly(OutboxEventType.PAYMENT_CREATED.name)
    }

    private fun statusInDb(id: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM payments WHERE id = ?", String::class.java, id)!!

}
