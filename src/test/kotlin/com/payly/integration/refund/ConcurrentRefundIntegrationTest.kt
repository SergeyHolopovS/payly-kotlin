package com.payly.integration.refund

import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.PaymentStatus
import com.payly.support.AbstractIntegrationTest
import com.payly.support.fixtures.aCreateRefundRequest
import com.payly.support.security.WithMockMerchant
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.security.test.context.TestSecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Два одновременных возврата по одному платежу не должны вместе превысить его сумму.
 *
 * Use case проверяет лимит по прочитанному refundedAmount, поэтому без защиты оба
 * запроса прочитали бы "возвращено 0" и оба прошли бы проверку. Защищает optimistic
 * lock на платеже: второй запрос при записи платежа упирается в изменённую версию,
 * и его транзакция (вместе с уже вставленным возвратом) откатывается.
 *
 * Гонка воспроизводится детерминированно:
 *  1. первый запрос обновил платёж (и держит блокировку строки), но "зависает"
 *     на записи в outbox — транзакция не закоммичена;
 *  2. второй запрос читает ещё старое состояние платежа, проходит проверку лимита
 *     и встаёт в очередь на блокировку строки — это видно в pg_stat_activity;
 *  3. только после этого первый запрос отпускается и коммитится.
 *
 * Тот же набор @MockitoSpyBean, что в CancelPaymentRollbackIntegrationTest, —
 * Spring переиспользует закэшированный контекст.
 */
@WithMockMerchant
class ConcurrentRefundIntegrationTest : AbstractIntegrationTest() {

    @MockitoSpyBean
    private lateinit var createOutbox: CreateOutboxEventUseCase

    // По потоку на каждый конкурентный запрос
    private val executor = Executors.newFixedThreadPool(2)

    @AfterEach
    fun shutdownExecutor() {
        executor.shutdownNow()
    }

    @Test
    fun `одновременные возвраты не превышают сумму платежа`() {
        val paymentId = payments.create(amount = 10_000, status = PaymentStatus.SUCCEEDED)
        val firstEnteredOutbox = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstCall = AtomicBoolean(true)

        // Зависает только первый вызов outbox для возврата; остальные вызовы
        // (второй запрос, планировщики) идут в настоящий бин
        doAnswer {
            if (firstCall.getAndSet(false)) {
                firstEnteredOutbox.countDown()
                check(releaseFirst.await(10, TimeUnit.SECONDS)) { "тест не отпустил первый запрос" }
            }
            it.callRealMethod()
        }.whenever(createOutbox).execute(argThat { eventType == OutboxEventType.REFUND_SUCCEEDED.name })

        // SecurityContext от @WithMockMerchant живёт в ThreadLocal потока теста —
        // передаём его в фоновые запросы явно
        val merchantContext = TestSecurityContextHolder.getContext()

        // Каждый запрос возвращает 7_000 из 10_000: по отдельности оба валидны, вместе — нет
        fun refund7000() = executor.submit<Int> {
            mockMvc.perform(
                post("/api/v1/payment/{id}/refund", paymentId)
                    .with(securityContext(merchantContext))
                    .contentType(MediaType.APPLICATION_JSON)
                    // Разные ключи: это два разных возврата, а не повтор одного
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .content(toJson(aCreateRefundRequest(amount = 7_000)))
            ).andReturn().response.status
        }

        // 1. Первый запрос доходит до outbox и зависает с незакоммиченной транзакцией
        val first = refund7000()
        assertThat(firstEnteredOutbox.await(10, TimeUnit.SECONDS)).isTrue()

        // 2. Второй запрос запускается и ждёт блокировку строки платежа
        val second = refund7000()
        await().atMost(Duration.ofSeconds(10)).until { sessionsWaitingForLock() > 0 }

        // 3. Первый коммитится, второй продолжает и натыкается на изменённую версию
        releaseFirst.countDown()
        val statuses = listOf(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))

        // Ровно один возврат успешен
        assertThat(statuses.count { it == 201 }).isEqualTo(1)
        assertThat(statuses.count { it in 200..299 }).isEqualTo(1)

        // Главный инвариант: в БД один возврат, и вернули не больше суммы платежа
        val refunds = jdbcTemplate.queryForList(
            "SELECT amount FROM refunds WHERE payment_id = ?", Long::class.java, paymentId,
        )
        assertThat(refunds).containsExactly(7_000L)

        val refundedAmount = jdbcTemplate.queryForObject(
            "SELECT refunded_amount FROM payments WHERE id = ?", Long::class.java, paymentId,
        )
        assertThat(refundedAmount).isEqualTo(7_000L)
    }

    /** Сколько сессий БД сейчас ждут чужую блокировку (например, строки платежа). */
    private fun sessionsWaitingForLock(): Int =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND datname = current_database()",
            Int::class.java,
        )!!

}
