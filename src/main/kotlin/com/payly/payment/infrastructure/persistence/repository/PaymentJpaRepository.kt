package com.payly.payment.infrastructure.persistence.repository

import com.payly.payment.infrastructure.persistence.entity.PaymentJpaEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param


interface PaymentJpaRepository: JpaRepository<PaymentJpaEntity, String>, JpaSpecificationExecutor<PaymentJpaEntity> {

    override fun findAll(spec: Specification<PaymentJpaEntity>, pageable: Pageable): Page<PaymentJpaEntity>

    @Query(
        value = """
            SELECT * FROM payments
            WHERE status = 'PROCESSING'
              AND updated_at + (
                    CASE
                      WHEN card_last4 = '3220' THEN 30
                      ELSE :minDelaySeconds
                           + (abs(hashtext(id)) % (:maxDelaySeconds - :minDelaySeconds + 1))
                    END
                  ) * INTERVAL '1 second' <= now()
            ORDER BY updated_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        """, nativeQuery = true
    )
    fun lockDuePayments(
        @Param("minDelaySeconds") minDelaySeconds: Int,
        @Param("maxDelaySeconds") maxDelaySeconds: Int,
        @Param("batchSize") batchSize: Int
    ): List<PaymentJpaEntity>

}