package com.payly.share.infrastructure.idempotency.persistence.repository

import com.payly.share.infrastructure.idempotency.persistence.entity.IdempotencyRecordJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IdempotencyRecordJpaRepository : JpaRepository<IdempotencyRecordJpaEntity, UUID> {

    fun findByMerchantIdAndKey(merchantId: UUID, key: String): IdempotencyRecordJpaEntity?

}
