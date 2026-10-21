package com.payly.payment.infrastructure.persistence.repository

import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.domain.repository.PaymentRepository
import com.payly.payment.infrastructure.mapper.PaymentMapper
import com.payly.payment.infrastructure.persistence.entity.PaymentJpaEntity
import com.payly.share.infrastructure.exceptions.payment.PaymentNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class PaymentRepositoryImpl(
    private val repository: PaymentJpaRepository,
    private val mapper: PaymentMapper
) : PaymentRepository {

    override fun save(
        merchantId: UUID,
        amount: Long,
        currency: String,
        description: String?,
        metadata: Map<String, String>,
        cardLast4: String
    ): Payment
        = mapper.toModel(
            repository.saveAndFlush(
                PaymentJpaEntity(
                    merchantId = merchantId,
                    amount = amount,
                    currency = currency,
                    description = description,
                    metadata = metadata,
                    cardLast4 = cardLast4
                )
            )
        )

    // saveAndFlush, а не save (и здесь, и в save выше): @Version и @CreationTimestamp
    // проставляются только при flush. Без него в модель попадают старая версия
    // и createdAt из конструктора сущности; при следующем update это даёт лишний
    // UPDATE с инкрементом версии, и финализация платежа в другой транзакции
    // (CreatePaymentUseCase.finalize) падает с ObjectOptimisticLockingFailureException
    override fun update(payment: Payment): Payment
        = mapper.toModel(repository.saveAndFlush(mapper.toEntity(payment)))

    override fun findDueForFinalization(
        minDelaySeconds: Int,
        maxDelaySeconds: Int,
        batchSize: Int,
    ): List<Payment>
        = repository
            .lockDuePayments(minDelaySeconds, maxDelaySeconds, batchSize)
            .map(mapper::toModel)

    override fun getById(id: String): Payment
        = mapper.toModel(
            repository
                .findById(id)
                .orElseThrow { PaymentNotFoundException() }
        )

    override fun getList(
        merchantId: UUID,
        createdFrom: Instant?,
        createdTo: Instant?,
        status: PaymentStatus?,
        pageable: Pageable
    ): Page<Payment> =
        repository.findAll(
            combineSpecifications(
                PaymentJpaSpecifications.statusEquals(status),
                PaymentJpaSpecifications.createdBetween(
                    createdFrom,
                    createdTo
                ),
                PaymentJpaSpecifications.merchantIdEquals(merchantId)
            ),
            pageable
        ).map(mapper::toModel)

    private fun combineSpecifications(
        vararg specifications: Specification<PaymentJpaEntity>?
    ): Specification<PaymentJpaEntity> =
        specifications
            .filterNotNull()
            .reduceOrNull { result, specification ->
                result.and(specification)
            }
            ?: Specification { _, _, criteriaBuilder ->
                criteriaBuilder.conjunction()
            }

}