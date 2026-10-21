package com.payly.refund.infrastructure.persistence.repository

import com.payly.refund.domain.model.Refund
import com.payly.refund.domain.repository.RefundRepository
import com.payly.refund.infrastructure.mapper.RefundMapper
import com.payly.refund.infrastructure.persistence.entity.RefundJpaEntity
import com.payly.share.infrastructure.exceptions.refund.RefundNotFoundException
import org.springframework.stereotype.Repository

@Repository
class RefundRepositoryImpl(
    private val repository: RefundJpaRepository,
    private val mapper: RefundMapper
) : RefundRepository {

    override fun save(
        paymentId: String,
        amount: Long,
    ): Refund
        = mapper.toModel(
            repository.save(
                RefundJpaEntity(
                    paymentId = paymentId,
                    amount = amount,
                )
            )
        )

    override fun update(refund: Refund): Refund
        = mapper.toModel(repository.save(mapper.toEntity(refund)))

    override fun getById(id: String): Refund
        = mapper.toModel(
            repository
                .findById(id)
                .orElseThrow { RefundNotFoundException() }
        )

    override fun getByPaymentId(paymentId: String): List<Refund>
        = repository.findAllByPaymentId(paymentId).map(mapper::toModel)

}
