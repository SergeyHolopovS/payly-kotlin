package com.payly.payment.infrastructure.persistence.entity

import com.payly.payment.domain.model.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payments")
data class PaymentJpaEntity(

    @Id
    @Column(name = "id", nullable = false, unique = true)
    val id: String = "pay_" + UUID.randomUUID().toString().replace("-", ""),

    @Column(name = "merchant_id", nullable = false)
    val merchantId: UUID,

    @Column(name = "amount", nullable = false)
    val amount: Long,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    val status: PaymentStatus = PaymentStatus.CREATED,

    @Column(name = "description")
    val description: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    val metadata: Map<String, Any?> = emptyMap(),

    @Column(name = "card_last4", nullable = false, length = 4)
    val cardLast4: String,

    @Column(name = "failure_reason")
    val failureReason: String? = null,

    @Column(name = "refunded_amount", nullable = false)
    val refundedAmount: Long = 0,

    @Version
    @Column(name = "version", nullable = false)
    val version: Long = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),

)
