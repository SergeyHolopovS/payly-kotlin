package com.payly.webhook.infrastructure.worker

import com.payly.webhook.infrastructure.persistense.entity.DeliveryStatus
import com.payly.webhook.infrastructure.persistense.entity.WebhookDeliveryJpaEntity
import com.payly.webhook.infrastructure.persistense.repository.WebhookDeliveryJpaRepository
import com.payly.webhook.infrastructure.utils.RetrySchedule
import com.payly.webhook.infrastructure.utils.WebhookResult
import com.payly.webhook.infrastructure.utils.WebhookSender
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class WebhookDeliveryWorker(
    private val repository: WebhookDeliveryJpaRepository,
    private val sender: WebhookSender,
    private val meterRegistry: MeterRegistry,
) {

    @Scheduled(fixedRate = 10_000)
    fun processPending() {
        val due = repository.findByStatusAndNextAttemptAtLessThanEqual(
            DeliveryStatus.PENDING, Instant.now()
        )
        due.forEach(::attempt)
    }

    // Без транзакции вокруг попытки — намеренно. @Transactional здесь всё равно
    // не работал (вызов через this мимо Spring-прокси), а рабочая транзакция держала бы
    // соединение с БД всё время HTTP-запроса к продавцу (до 15 с таймаутов на каждую доставку).
    // Запись в БД одна — save() в конце, и он сам выполняется в своей транзакции
    private fun attempt(delivery: WebhookDeliveryJpaEntity) {
        delivery.attempts += 1

        val sample = Timer.start(meterRegistry)
        val result = sender.send(delivery)
        sample.stop(
            Timer.builder("payly.webhook.delivery.attempt")
                .description("Попытки доставки вебхука продавцу")
                .tag("result", resultTag(result))
                .register(meterRegistry)
        )

        when (result) {
            is WebhookResult.Success -> {
                delivery.status = DeliveryStatus.DELIVERED
                delivery.lastResponseCode = result.code
            }
            is WebhookResult.Failure -> {
                delivery.lastResponseCode = result.code
                delivery.lastError = result.error
                val next = RetrySchedule.nextAttemptAt(delivery.attempts)
                if (next == null) {
                    delivery.status = DeliveryStatus.FAILED
                    recordGaveUp("retries_exhausted")
                } else  {
                    delivery.nextAttemptAt = next
                }
            }

            WebhookResult.Skipped -> {
                delivery.status = DeliveryStatus.FAILED
                recordGaveUp("skipped")
            }
        }
        repository.save(delivery)
    }

    private fun resultTag(result: WebhookResult): String = when (result) {
        is WebhookResult.Success -> "success"
        is WebhookResult.Failure -> "failure"
        WebhookResult.Skipped -> "skipped"
    }

    // Доставка окончательно провалена — продавец событие уже не получит
    private fun recordGaveUp(reason: String) {
        Counter.builder("payly.webhook.deliveries.failed")
            .description("Вебхуки, доставку которых прекратили")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment()
    }

}