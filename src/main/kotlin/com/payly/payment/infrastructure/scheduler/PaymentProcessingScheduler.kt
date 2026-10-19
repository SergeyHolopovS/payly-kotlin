package com.payly.payment.infrastructure.scheduler

import com.payly.outbox.application.usecase.create.CreateOutboxEventCommand
import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.domain.model.toEventPayload
import com.payly.payment.domain.repository.PaymentRepository
import com.payly.payment.infrastructure.gateway.TestCardOutcome
import com.payly.payment.infrastructure.properties.PaymentProcessingProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * Асинхронно "доводит" платежи, застрявшие в PROCESSING, до финального
 * статуса — имитирует задержку ответа платёжного гейтвея, не блокируя
 * HTTP-поток запроса создания платежа (ту же задачу для синхронного пути
 * решает [com.payly.payment.infrastructure.gateway.TestModePaymentGateway]).
 */
@Component
class PaymentProcessingScheduler(
    private val paymentRepository: PaymentRepository,
    private val createOutbox: CreateOutboxEventUseCase,
    private val properties: PaymentProcessingProperties,
    private val transactionTemplate: TransactionTemplate,
) {

    @Scheduled(fixedDelayString = "\${payment.processing.poll-interval-ms:1000}")
    fun processDuePayments() {
        var processed: Int
        do {
            processed = processBatch()
        } while (processed == properties.batchSize)
    }

    // TransactionTemplate вместо @Transactional: processDuePayments() вызывает
    // этот метод через this (self-invocation), а Spring-прокси не перехватывает
    // такие вызовы — аннотация @Transactional на этом методе просто не сработала бы
    private fun processBatch(): Int =
        transactionTemplate.execute { _ ->
            val due = paymentRepository.findDueForFinalization(
                minDelaySeconds = properties.minDelaySeconds,
                maxDelaySeconds = properties.maxDelaySeconds,
                batchSize = properties.batchSize,
            )

            due.forEach(::finalizePayment)

            due.size
        }

    private fun finalizePayment(payment: Payment) {
        val outcome = TestCardOutcome.resolve(payment.cardLast4)
        val finalStatus = if (outcome.isSuccess) PaymentStatus.SUCCEEDED else PaymentStatus.FAILED

        val finalized = payment
            .transitionTo(finalStatus)
            .copy(failureReason = outcome.failureReason)

        val updated = paymentRepository.update(finalized)

        createOutbox.execute(
            CreateOutboxEventCommand(
                aggregateId = updated.id,
                eventType = eventTypeFor(finalStatus).name,
                payload = updated.toEventPayload(),
            )
        )
    }

    private fun eventTypeFor(status: PaymentStatus): OutboxEventType = when (status) {
        PaymentStatus.SUCCEEDED -> OutboxEventType.PAYMENT_SUCCEEDED
        PaymentStatus.FAILED -> OutboxEventType.PAYMENT_FAILED
        else -> error("Неожиданный финальный статус: $status")
    }

}
