package com.payly.payment.application.usecase.create

import com.payly.outbox.application.usecase.create.CreateOutboxEventCommand
import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.domain.repository.PaymentRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CreatePaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val createOutbox: CreateOutboxEventUseCase
) {

    @Transactional
    fun execute(command: CreatePaymentCommand): Payment {
        // Номер карты валидируется по Луну на уровне @Valid (CardRequest.number),
        // так что сюда можно попасть только с уже прошедшей валидацию картой.
        // Сохраняем новый платёж — статус CREATED подставляется автоматически
        val created = paymentRepository.save(
            merchantId = command.merchantId,
            amount = command.amount,
            currency = command.currency,
            description = command.description,
            metadata = command.metadata,
            cardLast4 = command.cardLast4,
        )

        // Синхронно переводим платёж в PROCESSING сразу после создания
        val payment = paymentRepository.update(
            created.transitionTo(
                PaymentStatus.PROCESSING
            )
        )

        // Создаём OutboxEvent в той же транзакции, что и платёж,
        // чтобы запись события никогда не "потерялась" относительно самого платежа
        createOutbox.execute(
            CreateOutboxEventCommand(
                aggregateId = payment.id,
                eventType = OutboxEventType.PAYMENT_CREATED.name,
                payload = mapOf(
                    "paymentId" to payment.id,
                    "merchantId" to payment.merchantId.toString(),
                    "amount" to payment.amount.toString(),
                    "currency" to payment.currency,
                    "status" to payment.status.name,
                )
            )
        )

        return payment
    }

}