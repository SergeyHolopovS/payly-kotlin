package com.payly.payment.application.usecase.get

import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.repository.PaymentRepository
import com.payly.share.infrastructure.exceptions.payment.PaymentNotFoundException
import org.springframework.stereotype.Component

@Component
class GetPaymentUseCase(
    val repository: PaymentRepository
) {

    fun execute(command: GetPaymentCommand): Payment {
        // Ищем платёж в базе данных
        val payment = repository.getById(command.id)

        if (command.merchantId != payment.merchantId)
            throw PaymentNotFoundException()

        return payment
    }

}