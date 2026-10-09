package com.payly.payment.infrastructure.persistence.repository

import com.payly.payment.infrastructure.persistence.entity.PaymentJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentJpaRepository: JpaRepository<PaymentJpaEntity, UUID> {
}