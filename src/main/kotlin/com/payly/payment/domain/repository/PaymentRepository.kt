package com.payly.payment.domain.repository

import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

interface PaymentRepository {

    fun save(
        merchantId: UUID,
        amount: Long,
        currency: String,
        description: String? = null,
        metadata: Map<String, String> = emptyMap(),
        cardLast4: String,
    ): Payment

    fun update(payment: Payment): Payment

    /**
     * Выбирает и блокирует (SELECT ... FOR UPDATE SKIP LOCKED) пачку платежей
     * в статусе PROCESSING, для которых истёк имитируемый срок обработки —
     * для использования планировщиком [com.payly.payment.infrastructure.scheduler.PaymentProcessingScheduler].
     */
    fun findDueForFinalization(
        minDelaySeconds: Int,
        maxDelaySeconds: Int,
        batchSize: Int,
    ): List<Payment>

    fun getById(id: String): Payment

    fun getList(
        merchantId: UUID,
        createdFrom: Instant? = null,
        createdTo: Instant? = null,
        status: PaymentStatus? = null,
        pageable: Pageable
    ): Page<Payment>

}