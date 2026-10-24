package com.payly.payment.application.usecase.cancel

import com.payly.outbox.application.usecase.create.CreateOutboxEventCommand
import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.application.metrics.PaymentMetrics
import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.domain.model.toEventPayload
import com.payly.payment.domain.repository.PaymentRepository
import com.payly.share.infrastructure.exceptions.payment.PaymentNotFoundException
import com.payly.share.infrastructure.exceptions.payment.PaymentProcessingException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CancelPaymentUseCase (
    private val repository: PaymentRepository,
    private val outbox: CreateOutboxEventUseCase,
    private val paymentMetrics: PaymentMetrics,
) {

    @Transactional
    fun execute(command: CancelPaymentCommand): Payment {
        // Находим платёж в базе данных
        val payment = repository.getById(command.id)

        // 404 если продавец не владеет платежём
        if (payment.merchantId != command.merchantId)
            throw PaymentNotFoundException()

        // Платёж уже в гейтвее — отдельная ошибка вместо общей "недопустимый переход":
        // клиенту нужно понять, что результат ещё будет, и его стоит подождать
        if (payment.status == PaymentStatus.PROCESSING)
            throw PaymentProcessingException()

        // Обновляем модель
        val updatedPayment = payment.transitionTo(PaymentStatus.CANCELED)

        // Пробуем отменить
        repository.update(updatedPayment)

        // Записываем в Outbox
        outbox.execute(
            CreateOutboxEventCommand(
                aggregateId = updatedPayment.id,
                eventType = OutboxEventType.PAYMENT_CANCELLED.name,
                payload = updatedPayment.toEventPayload()
            )
        )

        // Учтётся только после коммита транзакции
        paymentMetrics.paymentCanceled(updatedPayment)

        // Возвращаем
        return updatedPayment
    }

}