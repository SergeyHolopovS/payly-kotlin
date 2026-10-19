package com.payly.payment.application.usecase.create

import com.payly.outbox.application.usecase.create.CreateOutboxEventCommand
import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.gateway.PaymentGateway
import com.payly.payment.domain.gateway.PaymentGatewayResult
import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.domain.model.toEventPayload
import com.payly.payment.domain.repository.PaymentRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class CreatePaymentUseCase(
    private val paymentRepository: PaymentRepository,
    private val createOutbox: CreateOutboxEventUseCase,
    private val paymentGateway: PaymentGateway,
    private val transactionTemplate: TransactionTemplate,
) {

    fun execute(command: CreatePaymentCommand): Payment {
        // Номер карты валидируется по Луну на уровне @Valid (CardRequest.number),
        // так что сюда можно попасть только с уже прошедшей валидацию картой.
        val processingPayment = createAndStartProcessing(command)

        // Поход в гейтвей выполняется синхронно и ОСОЗНАННО вне транзакции:
        // для тестовой карты имитации долгой обработки это ~30 секунд,
        // и держать на это время открытую транзакцию/соединение с БД нельзя
        val result = paymentGateway.charge(command.cardNumber)

        return finalize(processingPayment, result)
    }

    private fun createAndStartProcessing(command: CreatePaymentCommand): Payment =
        transactionTemplate.execute { _ ->
            // Сохраняем новый платёж — статус CREATED подставляется автоматически
            val created = paymentRepository.save(
                merchantId = command.merchantId,
                amount = command.amount,
                currency = command.currency,
                description = command.description,
                metadata = command.metadata,
                cardLast4 = command.cardNumber.takeLast(4),
            )

            // Синхронно переводим платёж в PROCESSING сразу после создания
            val payment = paymentRepository.update(created.transitionTo(PaymentStatus.PROCESSING))

            // Создаём OutboxEvent в той же транзакции, что и платёж,
            // чтобы запись события никогда не "потерялась" относительно самого платежа
            createOutbox.execute(
                CreateOutboxEventCommand(
                    aggregateId = payment.id,
                    eventType = OutboxEventType.PAYMENT_CREATED.name,
                    payload = payment.toEventPayload(),
                )
            )

            payment
        }

    private fun finalize(payment: Payment, result: PaymentGatewayResult): Payment =
        transactionTemplate.execute { _ ->
            val finalized = when (result) {
                is PaymentGatewayResult.Approved ->
                    payment.transitionTo(PaymentStatus.SUCCEEDED)

                is PaymentGatewayResult.Declined ->
                    payment
                        .transitionTo(PaymentStatus.FAILED)
                        .copy(failureReason = result.reason.code)
            }

            paymentRepository.update(finalized)
        }

}