package com.payly.payment.infrastructure.web

import com.payly.payment.application.usecase.create.CreatePaymentCommand
import com.payly.payment.application.usecase.create.CreatePaymentUseCase
import com.payly.payment.application.usecase.get.GetPaymentCommand
import com.payly.payment.application.usecase.get.GetPaymentUseCase
import com.payly.payment.application.usecase.list.GetListPaymentsCommand
import com.payly.payment.application.usecase.list.GetListPaymentsUseCase
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.infrastructure.mapper.PaymentMapper
import com.payly.payment.infrastructure.web.dto.request.CreatePaymentRequest
import com.payly.payment.infrastructure.web.dto.response.PaymentResponse
import com.payly.share.security.annotation.CurrentMerchantId
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/payment")
class PaymentController(
    private val create: CreatePaymentUseCase,
    private val get: GetPaymentUseCase,
    private val list: GetListPaymentsUseCase,
    private val mapper: PaymentMapper,
) {

    @PostMapping
    fun create(
        @RequestBody @Valid createPaymentRequest: CreatePaymentRequest,
        @CurrentMerchantId merchantId: UUID,
    ): ResponseEntity<PaymentResponse> {
        val payment = create.execute(
            CreatePaymentCommand(
                merchantId = merchantId,
                currency = createPaymentRequest.currency,
                amount = createPaymentRequest.amount,
                description = createPaymentRequest.description,
                metadata = createPaymentRequest.metadata,
                cardNumber = createPaymentRequest.card.number,
            )
        )

        return ResponseEntity.ok(mapper.toResponse(payment))
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
        @CurrentMerchantId merchantId: UUID
    ): ResponseEntity<PaymentResponse> {
        val payment = get.execute(
            GetPaymentCommand(
                id = id,
                merchantId = merchantId
            )
        )

        return ResponseEntity.ok(
            mapper.toResponse(payment)
        )
    }

    @GetMapping
    fun list(
        @PathVariable createdFrom: Instant,
        @PathVariable createdTo: Instant,
        @PathVariable status: PaymentStatus,
        @CurrentMerchantId merchantId: UUID,
        pageable: Pageable
    ): ResponseEntity<List<PaymentResponse>> {
        val payments = list.execute(
            GetListPaymentsCommand(
                merchantId = merchantId,
                createdFrom = createdFrom,
                createdTo = createdTo,
                status = status,
                pageable = pageable,
            )
        )

        return ResponseEntity.ok(
            payments
                .map(mapper::toResponse)
                .toList()
        )
    }

}