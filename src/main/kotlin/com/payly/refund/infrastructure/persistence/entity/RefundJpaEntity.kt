package com.payly.refund.infrastructure.persistence.entity

import com.payly.refund.domain.model.RefundStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refunds")
class RefundJpaEntity(

    @Id
    @Column(name = "id", nullable = false, unique = true)
    var id: String = "re_" + UUID.randomUUID().toString().replace("-", ""),

    @Column(name = "payment_id", nullable = false)
    var paymentId: String,

    @Column(name = "amount", nullable = false)
    var amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: RefundStatus = RefundStatus.PENDING,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

)
