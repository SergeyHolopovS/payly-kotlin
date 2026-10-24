package com.payly.unit.webhook

import com.payly.support.UnitTest
import com.payly.webhook.infrastructure.persistense.entity.DeliveryStatus
import com.payly.webhook.infrastructure.persistense.entity.WebhookDeliveryJpaEntity
import com.payly.webhook.infrastructure.persistense.repository.WebhookDeliveryJpaRepository
import com.payly.webhook.infrastructure.utils.WebhookResult
import com.payly.webhook.infrastructure.utils.WebhookSender
import com.payly.webhook.infrastructure.worker.WebhookDeliveryWorker
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

/**
 * Unit-тесты [WebhookDeliveryWorker]: как результат попытки меняет состояние
 * доставки и что оно сохраняется в БД. Отправка и репозиторий — моки.
 */
@UnitTest
class WebhookDeliveryWorkerTest {

    @Mock
    private lateinit var repository: WebhookDeliveryJpaRepository

    @Mock
    private lateinit var sender: WebhookSender

    private val meterRegistry = SimpleMeterRegistry()

    private lateinit var worker: WebhookDeliveryWorker

    @BeforeEach
    fun setUp() {
        worker = WebhookDeliveryWorker(repository, sender, meterRegistry)
    }

    @Test
    fun `успешная доставка помечается DELIVERED и сохраняется`() {
        val delivery = givenDueDelivery()
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Success(200))

        worker.processPending()

        assertThat(delivery.status).isEqualTo(DeliveryStatus.DELIVERED)
        assertThat(delivery.attempts).isEqualTo(1)
        assertThat(delivery.lastResponseCode).isEqualTo(200)
        verify(repository).save(delivery)
    }

    @Test
    fun `неудачная попытка остаётся PENDING и переносится на потом`() {
        val delivery = givenDueDelivery()
        val before = delivery.nextAttemptAt
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Failure(500, "non-2xx response"))

        worker.processPending()

        assertThat(delivery.status).isEqualTo(DeliveryStatus.PENDING)
        assertThat(delivery.nextAttemptAt).isAfter(before)
        assertThat(delivery.lastError).isEqualTo("non-2xx response")
        verify(repository).save(delivery)
    }

    @Test
    fun `после последнего ретрая доставка помечается FAILED`() {
        // 7 попыток уже было — эта восьмая и последняя (см. RetrySchedule.MAX_ATTEMPTS)
        val delivery = givenDueDelivery(attempts = 7)
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Failure(0, "connection refused"))

        worker.processPending()

        assertThat(delivery.status).isEqualTo(DeliveryStatus.FAILED)
        assertThat(meterRegistry.get("payly.webhook.deliveries.failed").tags("reason", "retries_exhausted").counter().count())
            .isEqualTo(1.0)
        verify(repository).save(delivery)
    }

    @Test
    fun `пропущенная доставка помечается FAILED без ретраев`() {
        val delivery = givenDueDelivery()
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Skipped)

        worker.processPending()

        assertThat(delivery.status).isEqualTo(DeliveryStatus.FAILED)
        assertThat(meterRegistry.get("payly.webhook.deliveries.failed").tags("reason", "skipped").counter().count())
            .isEqualTo(1.0)
        verify(repository).save(delivery)
    }

    @Test
    fun `код ответа продавца сохраняется и при неудачной попытке`() {
        val delivery = givenDueDelivery()
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Failure(503, "non-2xx response"))

        worker.processPending()

        assertThat(delivery.lastResponseCode).isEqualTo(503)
        assertThat(delivery.attempts).isEqualTo(1)
    }

    @Test
    fun `первый ретрай назначается примерно через минуту`() {
        val delivery = givenDueDelivery()
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Failure(500, "non-2xx response"))

        val before = Instant.now()
        worker.processPending()

        // 1 минута ± 20% джиттера (RetrySchedule)
        assertThat(delivery.nextAttemptAt).isBetween(before.plusSeconds(47), Instant.now().plusSeconds(73))
    }

    @Test
    fun `ошибка одной доставки не мешает обработать остальные`() {
        val failing = aDelivery()
        val ok = aDelivery()
        whenever(repository.findByStatusAndNextAttemptAtLessThanEqual(eq(DeliveryStatus.PENDING), any()))
            .thenReturn(listOf(failing, ok))
        whenever(sender.send(failing)).thenReturn(WebhookResult.Failure(0, "connection refused"))
        whenever(sender.send(ok)).thenReturn(WebhookResult.Success(200))

        worker.processPending()

        assertThat(failing.status).isEqualTo(DeliveryStatus.PENDING)
        assertThat(ok.status).isEqualTo(DeliveryStatus.DELIVERED)
        verify(repository).save(failing)
        verify(repository).save(ok)
    }

    @Test
    fun `нечего доставлять — ничего не отправляется и не сохраняется`() {
        whenever(repository.findByStatusAndNextAttemptAtLessThanEqual(eq(DeliveryStatus.PENDING), any()))
            .thenReturn(emptyList())

        worker.processPending()

        verifyNoInteractions(sender)
        verify(repository, never()).save(any<WebhookDeliveryJpaEntity>())
    }

    @Test
    fun `каждая попытка пишется в таймер с результатом`() {
        val delivery = givenDueDelivery()
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Success(200))

        worker.processPending()

        assertThat(meterRegistry.get("payly.webhook.delivery.attempt").tags("result", "success").timer().count())
            .isEqualTo(1)
    }

    private fun aDelivery(attempts: Int = 0) = WebhookDeliveryJpaEntity(
        merchantId = UUID.randomUUID(),
        eventType = "PAYMENT_SUCCEEDED",
        payload = "{}",
        attempts = attempts,
        nextAttemptAt = Instant.now().minusSeconds(1),
    )

    private fun givenDueDelivery(attempts: Int = 0): WebhookDeliveryJpaEntity {
        val delivery = aDelivery(attempts)
        whenever(repository.findByStatusAndNextAttemptAtLessThanEqual(eq(DeliveryStatus.PENDING), any()))
            .thenReturn(listOf(delivery))
        return delivery
    }

}
