package com.payly.outbox.infrastucture.persistence.repository

import com.payly.outbox.infrastucture.persistence.entity.OutboxEventJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OutboxEventJpaRepository : JpaRepository<OutboxEventJpaEntity, UUID> {
}