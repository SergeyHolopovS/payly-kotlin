package com.payly.payment.application.usecase.list

import com.payly.payment.domain.model.PaymentStatus
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

data class GetListPaymentsCommand(
    val merchantId: UUID,
    val createdFrom: Instant? = null,
    val createdTo: Instant? = null,
    val status: PaymentStatus? = null,
    val pageable: Pageable
)
