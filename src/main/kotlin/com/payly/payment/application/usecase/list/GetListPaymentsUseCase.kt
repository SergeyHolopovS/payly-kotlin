package com.payly.payment.application.usecase.list

import com.payly.payment.domain.repository.PaymentRepository
import org.springframework.stereotype.Component

@Component
class GetListPaymentsUseCase(
    private val repository: PaymentRepository
) {

    fun execute(command: GetListPaymentsCommand)
        // Ищем в базе данных по спецификации и возвращаем
        = repository.getList(
            merchantId = command.merchantId,
            createdFrom = command.createdFrom,
            createdTo = command.createdTo,
            status = command.status,
            pageable = command.pageable,
        )

}