package com.payly.webhook.infrastructure.messaging

import com.payly.webhook.domain.model.WebhookEvent
import com.payly.webhook.domain.model.WebhookEventData
import com.payly.webhook.infrastructure.persistense.entity.WebhookDeliveryJpaEntity
import com.payly.webhook.infrastructure.persistense.repository.WebhookDeliveryJpaRepository
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@Component
class PaymentStatusEventConsumer(
    private val repo: WebhookDeliveryJpaRepository,
    private val objectMapper: ObjectMapper
) {
    @KafkaListener(topics = ["\${outbox.publisher.topic:payment.events}"])
    fun onEvent(record: ConsumerRecord<String, String>) {
        val outboxEvent = objectMapper.readValue(record.value(), OutboxEventDto::class.java)
        val webhookEvent = WebhookEvent(
            id = "evt_${UUID.randomUUID()}",
            type = outboxEvent.eventType,
            createdAt = Instant.now(),
            data = WebhookEventData(outboxEvent.payload)
        )
        repo.save(
            WebhookDeliveryJpaEntity(
                merchantId = outboxEvent.merchantId,
                eventType = outboxEvent.eventType,
                payload = objectMapper.writeValueAsString(webhookEvent)
            )
        )
    }
}