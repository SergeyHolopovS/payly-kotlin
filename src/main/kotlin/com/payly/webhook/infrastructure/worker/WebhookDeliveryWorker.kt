package com.payly.webhook.infrastructure.worker

import com.payly.webhook.infrastructure.persistense.entity.DeliveryStatus
import com.payly.webhook.infrastructure.persistense.entity.WebhookDeliveryJpaEntity
import com.payly.webhook.infrastructure.persistense.repository.WebhookDeliveryJpaRepository
import com.payly.webhook.infrastructure.utils.RetrySchedule
import com.payly.webhook.infrastructure.utils.WebhookResult
import com.payly.webhook.infrastructure.utils.WebhookSender
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class WebhookDeliveryWorker(
    private val repository: WebhookDeliveryJpaRepository,
    private val sender: WebhookSender
) {

    @Scheduled(fixedRate = 10_000)
    fun processPending() {
        val due = repository.findByStatusAndNextAttemptAtLessThanEqual(
            DeliveryStatus.PENDING, Instant.now()
        )
        due.forEach { delivery -> attemt(delivery) }
    }

    @Transactional
    fun attemt(delivery: WebhookDeliveryJpaEntity) {
        delivery.attempts += 1
        when (val result = sender.send(delivery)) {
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
                } else  {
                    delivery.nextAttemptAt = next
                }
            }

            WebhookResult.Skipped -> delivery.status = DeliveryStatus.FAILED
        }
        repository.save(delivery)
    }

}