package com.payly.outbox.infrastucture.messaging

import java.time.Instant
import java.util.UUID

/**
 * Конверт события, который фактически уходит во внешнюю систему (Kafka).
 * Отделён от domain-модели [com.payly.outbox.domain.model.OutboxEvent],
 * чтобы формат на "проводе" можно было версионировать независимо от хранения.
 */
data class OutboxEventMessage(
    val eventId: UUID,
    val eventType: String,
    val aggregateId: String,
    val payload: Map<String, String>,
    val occurredAt: Instant,
)
