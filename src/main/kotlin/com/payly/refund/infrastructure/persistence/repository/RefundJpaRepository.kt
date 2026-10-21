package com.payly.refund.infrastructure.persistence.repository

import com.payly.refund.infrastructure.persistence.entity.RefundJpaEntity
import org.springframework.data.jpa.repository.JpaRepository

interface RefundJpaRepository : JpaRepository<RefundJpaEntity, String> {

    fun findAllByPaymentId(paymentId: String): List<RefundJpaEntity>

}
