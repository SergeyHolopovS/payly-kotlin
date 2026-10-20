package com.payly.outbox.infrastucture.persistence.repository

import com.payly.outbox.domain.model.OutboxEvent
import com.payly.outbox.domain.repository.OutboxEventRepository
import com.payly.outbox.infrastucture.mapper.OutboxEventMapper
import com.payly.outbox.infrastucture.persistence.entity.OutboxEventJpaEntity
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class OutboxEventRepositoryImpl(
    private val repository: OutboxEventJpaRepository,
    private val mapper: OutboxEventMapper
): OutboxEventRepository {

    override fun save(
        aggregateId: String,
        eventType: String,
        payload: Map<String, String>?
    ): OutboxEvent
        = mapper.toModel(
            repository.save(
                OutboxEventJpaEntity(
                    aggregateId = aggregateId,
                    eventType = eventType,
                    payload = payload,
                    publishedAt = null
                )
            )
        )

    override fun findUnpublished(limit: Int): List<OutboxEvent>
        = repository
            .findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, limit))
            .map(mapper::toModel)

    override fun markPublished(id: UUID, publishedAt: Instant) {
        repository.markPublished(id, publishedAt)
    }

}