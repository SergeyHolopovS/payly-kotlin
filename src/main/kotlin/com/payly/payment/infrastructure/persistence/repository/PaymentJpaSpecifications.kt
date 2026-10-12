package com.payly.payment.infrastructure.persistence.repository

import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.infrastructure.persistence.entity.PaymentJpaEntity
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.util.UUID

object PaymentJpaSpecifications {

    fun merchantIdEquals(merchantId: UUID): Specification<PaymentJpaEntity> {
        return Specification { root, _, criteriaBuilder ->
            criteriaBuilder.equal(
                root.get<UUID>("merchantId"),
                merchantId
            )
        }
    }

    fun statusEquals(status: PaymentStatus?): Specification<PaymentJpaEntity>? {
        if (status == null) return null
        return Specification { root, _, criteriaBuilder ->
            criteriaBuilder.equal(
                root.get<PaymentJpaEntity>("status"),
                status
            )
        }
    }

    fun createdBetween(
        createdFrom: Instant?,
        createdTo: Instant?
    ): Specification<PaymentJpaEntity>? {
        if (createdFrom == null && createdTo == null) return null
        return Specification { root, _, criteriaBuilder ->
            val createdAt = root.get<Instant>("createdAt")
            when {
                createdFrom != null && createdTo != null ->
                    criteriaBuilder.between(createdAt, createdFrom, createdTo)
                createdFrom != null ->
                    criteriaBuilder.greaterThanOrEqualTo(createdAt, createdFrom)
                else ->
                    criteriaBuilder.lessThanOrEqualTo(createdAt, createdTo)
            }
        }
    }

}