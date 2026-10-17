package com.payly.outbox.domain.model

import java.time.Instant
import java.util.UUID

data class OutboxEvent(
    val id: UUID,
    val aggregateId: String,
    val eventType: String,
    val payload: Map<String, String>? = null,
    val publishedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
)
