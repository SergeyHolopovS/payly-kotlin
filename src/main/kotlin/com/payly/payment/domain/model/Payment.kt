package com.payly.payment.domain.model

import com.payly.share.infrastructure.exceptions.payment.InvalidPaymentStatusTransitionException
import java.time.Instant
import java.util.UUID

data class Payment(
    val id: String,
    val merchantId: UUID,
    val amount: Long,
    val currency: String,
    val status: PaymentStatus,
    val description: String?,
    val metadata: Map<String, Any?>,
    val cardLast4: String,
    val failureReason: String?,
    val refundedAmount: Long,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {

    fun transitionTo(target: PaymentStatus): Payment {
        if (!status.canTransitionTo(target)) {
            throw InvalidPaymentStatusTransitionException(from = status, to = target)
        }

        return copy(status = target, updatedAt = Instant.now())
    }

    fun canTransitionTo(target: PaymentStatus): Boolean
        = status.canTransitionTo(target)

}