package com.payly.outbox.domain.repository

import com.payly.outbox.domain.model.OutboxEvent
import java.util.UUID

interface OutboxEventRepository {

    fun save(
        aggregateId: String,
        eventType: String,
        payload: Map<String, String>?
    ): OutboxEvent

    fun findUnpublished(limit: Int): List<OutboxEvent>

    fun markAsPublished(id: UUID)

}