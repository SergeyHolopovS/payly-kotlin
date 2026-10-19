package com.payly.share.infrastructure.idempotency.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "idempotency_records")
data class IdempotencyRecordJpaEntity(

    @Id
    @Column(name = "id", nullable = false, unique = true)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_id", nullable = false)
    val merchantId: UUID,

    @Column(name = "idempotency_key", nullable = false)
    val key: String,

    @Column(name = "request_hash", nullable = false)
    val requestHash: String,

    @Column(name = "response_status", nullable = false)
    val responseStatus: Int,

    @Column(name = "response_body", nullable = false)
    val responseBody: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

)
