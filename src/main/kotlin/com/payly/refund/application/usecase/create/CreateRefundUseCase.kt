package com.payly.refund.application.usecase.create

import com.payly.outbox.application.usecase.create.CreateOutboxEventCommand
import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.domain.repository.PaymentRepository
import com.payly.refund.domain.model.Refund
import com.payly.refund.domain.model.RefundStatus
import com.payly.refund.domain.model.toEventPayload
import com.payly.refund.domain.repository.RefundRepository
import com.payly.share.infrastructure.exceptions.payment.PaymentNotFoundException
import com.payly.share.infrastructure.exceptions.refund.InvalidRefundAmountException
import com.payly.share.infrastructure.exceptions.refund.PaymentNotRefundableException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

private val REFUNDABLE_STATUSES = setOf(PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED)

@Component
class CreateRefundUseCase(
    private val paymentRepository: PaymentRepository,
    private val refundRepository: RefundRepository,
    private val outbox: CreateOutboxEventUseCase,
) {

    @Transactional
    fun execute(command: CreateRefundCommand): Refund {
        // Находим платёж в базе данных
        val payment = paymentRepository.getById(command.paymentId)

        // 404 если продавец не владеет платежом
        if (payment.merchantId != command.merchantId)
            throw PaymentNotFoundException()

        // Возврат возможен только из статусов, в которых деньги списаны и ещё не возвращены полностью
        if (payment.status !in REFUNDABLE_STATUSES)
            throw PaymentNotRefundableException(payment.status)

        // Если сумма не передана — возвращаем остаток, а не полную сумму платежа,
        // иначе "полный" возврат после частичного всегда падал бы на проверке лимита
        val amount = command.amount ?: (payment.amount - payment.refundedAmount)

        if (amount <= 0 || payment.refundedAmount + amount > payment.amount)
            throw InvalidRefundAmountException()

        val refund = refundRepository.update(
            refundRepository.save(
                paymentId = payment.id,
                amount = amount,
            ).copy(status = RefundStatus.SUCCEEDED)
        )

        val updatedPayment = applyRefund(payment, amount)
        paymentRepository.update(updatedPayment)

        // Создаём OutboxEvent в той же транзакции, что и возврат,
        // чтобы запись события никогда не "потерялась" относительно самого возврата
        outbox.execute(
            CreateOutboxEventCommand(
                aggregateId = payment.id,
                eventType = OutboxEventType.REFUND_SUCCEEDED.name,
                payload = refund.toEventPayload(),
            )
        )

        return refund
    }

    private fun applyRefund(payment: Payment, amount: Long): Payment {
        val refundedAmount = payment.refundedAmount + amount
        val targetStatus = if (refundedAmount == payment.amount) PaymentStatus.REFUNDED else PaymentStatus.PARTIALLY_REFUNDED

        val transitioned = if (targetStatus == payment.status) payment else payment.transitionTo(targetStatus)

        return transitioned.copy(refundedAmount = refundedAmount)
    }

}
