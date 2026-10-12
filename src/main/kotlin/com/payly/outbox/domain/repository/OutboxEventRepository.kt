package com.payly.outbox.domain.repository

import com.payly.outbox.domain.model.OutboxEvent

interface OutboxEventRepository {

    fun save(
        aggregateId: String,
        eventType: String,
        payload: Map<String, String>?
    ): OutboxEvent

}