package com.payly.refund.infrastructure.web

import com.payly.refund.application.usecase.create.CreateRefundCommand
import com.payly.refund.application.usecase.create.CreateRefundUseCase
import com.payly.refund.infrastructure.mapper.RefundMapper
import com.payly.refund.infrastructure.web.dto.request.CreateRefundRequest
import com.payly.refund.infrastructure.web.dto.response.RefundResponse
import com.payly.share.infrastructure.idempotency.Idempotent
import com.payly.share.infrastructure.security.annotation.CurrentMerchantId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/payment/{id}/refund")
class RefundController(
    private val createRefund: CreateRefundUseCase,
    private val refundMapper: RefundMapper,
) {

    @Idempotent
    @PostMapping
    fun refund(
        @PathVariable id: String,
        @RequestBody(required = false) @Valid createRefundRequest: CreateRefundRequest?,
        @CurrentMerchantId merchantId: UUID,
    ): ResponseEntity<RefundResponse> {
        val refund = createRefund.execute(
            CreateRefundCommand(
                merchantId = merchantId,
                paymentId = id,
                amount = createRefundRequest?.amount,
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            refundMapper.toResponse(refund)
        )
    }

}
