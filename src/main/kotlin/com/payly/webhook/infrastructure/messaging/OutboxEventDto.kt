package com.payly.webhook.infrastructure.messaging

import java.time.Instant
import java.util.UUID

/**
 * DTO для десериализации сообщений из топика payment-events.
 * Зеркалит формат "на проводе" ([com.payly.outbox.infrastucture.messaging.OutboxEventMessage]),
 * который публикует OutboxEventPublisher.
 */
data class OutboxEventDto(
    val eventId: UUID,
    val eventType: String,
    val aggregateId: String,
    val payload: Map<String, String>,
    val occurredAt: Instant,
) {
    // merchantId кладётся в payload (см. Payment.toEventPayload()), а не в отдельное
    // поле конверта, поэтому достаём его оттуда для сохранения в WebhookDelivery
    val merchantId: UUID
        get() = UUID.fromString(payload.getValue("merchantId"))
}
