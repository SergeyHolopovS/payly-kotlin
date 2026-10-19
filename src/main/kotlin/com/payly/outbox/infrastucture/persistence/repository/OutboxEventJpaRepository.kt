package com.payly.outbox.infrastucture.persistence.repository

import com.payly.outbox.infrastucture.persistence.entity.OutboxEventJpaEntity
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface OutboxEventJpaRepository : JpaRepository<OutboxEventJpaEntity, UUID> {

    fun findByPublishedAtIsNullOrderByCreatedAtAsc(limit: Limit): List<OutboxEventJpaEntity>

    @Modifying
    @Query("update OutboxEventJpaEntity e set e.publishedAt = :publishedAt where e.id = :id")
    fun markAsPublished(id: UUID, publishedAt: Instant)

}