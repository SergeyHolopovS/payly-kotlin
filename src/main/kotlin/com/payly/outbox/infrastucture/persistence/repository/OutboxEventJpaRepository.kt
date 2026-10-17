package com.payly.outbox.infrastucture.persistence.repository

import com.payly.outbox.infrastucture.persistence.entity.OutboxEventJpaEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface OutboxEventJpaRepository : JpaRepository<OutboxEventJpaEntity, UUID> {

    fun findByPublishedAtIsNullOrderByCreatedAtAsc(pageable: Pageable): List<OutboxEventJpaEntity>

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEventJpaEntity e SET e.publishedAt = :publishedAt WHERE e.id = :id")
    fun markPublished(@Param("id") id: UUID, @Param("publishedAt") publishedAt: Instant): Int

}