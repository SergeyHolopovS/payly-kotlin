package com.payly.unit.webhook

import com.payly.support.UnitTest
import com.payly.support.data.TestMerchants
import com.payly.webhook.infrastructure.persistense.entity.DeliveryStatus
import com.payly.webhook.infrastructure.persistense.entity.WebhookDeliveryJpaEntity
import com.payly.webhook.infrastructure.persistense.repository.WebhookDeliveryJpaRepository
import com.payly.webhook.infrastructure.utils.RetrySchedule
import com.payly.webhook.infrastructure.utils.WebhookResult
import com.payly.webhook.infrastructure.utils.WebhookSender
import com.payly.webhook.infrastructure.worker.WebhookDeliveryWorker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Unit-тесты [WebhookDeliveryWorker]: как результат отправки меняет состояние
 * доставки (статус, счётчик попыток, время следующей попытки). Отправка
 * и репозиторий — моки; реальный HTTP проверяется в WebhookSenderTest и e2e.
 */
@UnitTest
class WebhookDeliveryWorkerTest {

    @Mock
    private lateinit var repository: WebhookDeliveryJpaRepository

    @Mock
    private lateinit var sender: WebhookSender

    private lateinit var worker: WebhookDeliveryWorker

    @BeforeEach
    fun setUp() {
        worker = WebhookDeliveryWorker(repository, sender)
    }

    // ---------- processPending ----------

    @Test
    fun `берёт в работу только PENDING-доставки, срок которых уже наступил`() {
        whenever(repository.findByStatusAndNextAttemptAtLessThanEqual(eq(DeliveryStatus.PENDING), any()))
            .thenReturn(emptyList())

        val before = Instant.now()
        worker.processPending()

        val now = argumentCaptor<Instant>()
        verify(repository).findByStatusAndNextAttemptAtLessThanEqual(eq(DeliveryStatus.PENDING), now.capture())
        assertThat(now.firstValue).isBetween(before, Instant.now())
        verify(sender, never()).send(any())
    }

    @Test
    fun `отправляет каждую найденную доставку`() {
        val first = aDelivery()
        val second = aDelivery()
        whenever(repository.findByStatusAndNextAttemptAtLessThanEqual(eq(DeliveryStatus.PENDING), any()))
            .thenReturn(listOf(first, second))
        whenever(sender.send(any())).thenReturn(WebhookResult.Success(200))

        worker.processPending()

        verify(sender).send(first)
        verify(sender).send(second)
        verify(repository, times(2)).save(any<WebhookDeliveryJpaEntity>())
    }

    // ---------- Успех ----------

    @Test
    fun `успешная отправка помечает доставку DELIVERED`() {
        val delivery = aDelivery()
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Success(204))

        worker.attemt(delivery)

        assertThat(delivery.status).isEqualTo(DeliveryStatus.DELIVERED)
        assertThat(delivery.attempts).isEqualTo(1)
        assertThat(delivery.lastResponseCode).isEqualTo(204)
        verify(repository).save(delivery)
    }

    // ---------- Ошибка и ретраи ----------

    @Test
    fun `неуспешная отправка планирует повтор и оставляет доставку в PENDING`() {
        val delivery = aDelivery()
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Failure(503, "non-2xx response"))

        worker.attemt(delivery)

        assertThat(delivery.status).isEqualTo(DeliveryStatus.PENDING)
        assertThat(delivery.attempts).isEqualTo(1)
        assertThat(delivery.lastResponseCode).isEqualTo(503)
        assertThat(delivery.lastError).isEqualTo("non-2xx response")
        // Первый повтор — через ~1 минуту (±20% джиттера)
        assertThat(delivery.nextAttemptAt).isBetween(
            Instant.now() + Duration.ofSeconds(47),
            Instant.now() + Duration.ofSeconds(73),
        )
        verify(repository).save(delivery)
    }

    @Test
    fun `сетевая ошибка тоже планирует повтор`() {
        val delivery = aDelivery()
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Failure(0, "Connection refused"))

        worker.attemt(delivery)

        assertThat(delivery.status).isEqualTo(DeliveryStatus.PENDING)
        assertThat(delivery.lastResponseCode).isEqualTo(0)
        assertThat(delivery.lastError).isEqualTo("Connection refused")
        assertThat(delivery.nextAttemptAt).isAfter(Instant.now())
    }

    @Test
    fun `после исчерпания попыток доставка помечается FAILED и больше не планируется`() {
        // Это последняя попытка: после неё attempts станет MAX_ATTEMPTS
        val nextAttemptAt = Instant.now().minusSeconds(1)
        val delivery = aDelivery(attempts = RetrySchedule.MAX_ATTEMPTS - 1, nextAttemptAt = nextAttemptAt)
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Failure(500, "non-2xx response"))

        worker.attemt(delivery)

        assertThat(delivery.status).isEqualTo(DeliveryStatus.FAILED)
        assertThat(delivery.attempts).isEqualTo(RetrySchedule.MAX_ATTEMPTS)
        assertThat(delivery.lastResponseCode).isEqualTo(500)
        assertThat(delivery.nextAttemptAt).isEqualTo(nextAttemptAt)
    }

    @Test
    fun `успех после нескольких неудач всё равно DELIVERED`() {
        val delivery = aDelivery(attempts = 3).apply {
            lastResponseCode = 500
            lastError = "non-2xx response"
        }
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Success(200))

        worker.attemt(delivery)

        assertThat(delivery.status).isEqualTo(DeliveryStatus.DELIVERED)
        assertThat(delivery.attempts).isEqualTo(4)
        assertThat(delivery.lastResponseCode).isEqualTo(200)
    }

    // ---------- Пропуск ----------

    @Test
    fun `доставка, которую некуда отправить, сразу помечается FAILED без ретраев`() {
        val nextAttemptAt = Instant.now().minusSeconds(1)
        val delivery = aDelivery(nextAttemptAt = nextAttemptAt)
        whenever(sender.send(delivery)).thenReturn(WebhookResult.Skipped)

        worker.attemt(delivery)

        assertThat(delivery.status).isEqualTo(DeliveryStatus.FAILED)
        assertThat(delivery.nextAttemptAt).isEqualTo(nextAttemptAt)
        verify(repository).save(delivery)
    }

    private fun aDelivery(
        attempts: Int = 0,
        nextAttemptAt: Instant = Instant.now(),
    ) = WebhookDeliveryJpaEntity(
        id = UUID.randomUUID(),
        merchantId = UUID.fromString(TestMerchants.DEFAULT_MERCHANT_ID),
        eventType = "PAYMENT_SUCCEEDED",
        payload = """{"id":"evt_1"}""",
        attempts = attempts,
        nextAttemptAt = nextAttemptAt,
    )

}
