package com.payly.payment.application.usecase.create

import com.payly.outbox.application.usecase.create.CreateOutboxEventCommand
import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.repository.PaymentRepository
import org.springframework.stereotype.Component

@Component
class CreatePaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val createOutbox: CreateOutboxEventUseCase
) {

    fun execute(command: CreatePaymentCommand): Payment {
        // Сохраняем новый платёж и возвращаем результат
        val payment = paymentRepository.save(
            merchantId = command.merchantId,
            amount = command.amount,
            currency = command.currency,
            description = command.description,
            metadata = command.metadata,
            cardLast4 = command.cardLast4,
        )

        // Создаём OutboxEvent
        createOutbox.execute(
            CreateOutboxEventCommand(
                aggregateId = payment.id,
                eventType = "CREATE_PAYMENT",
                payload = null
            )
        )

        return payment
    }

}