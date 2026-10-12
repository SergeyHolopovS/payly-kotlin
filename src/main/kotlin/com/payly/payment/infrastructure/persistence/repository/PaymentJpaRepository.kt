package com.payly.payment.infrastructure.persistence.repository

import com.payly.payment.infrastructure.persistence.entity.PaymentJpaEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface PaymentJpaRepository: JpaRepository<PaymentJpaEntity, String>, JpaSpecificationExecutor<PaymentJpaEntity> {

    override fun findAll(spec: Specification<PaymentJpaEntity>, pageable: Pageable): Page<PaymentJpaEntity>

}