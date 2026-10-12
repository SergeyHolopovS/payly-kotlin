package com.payly.payment.infrastructure.mapper

import com.payly.payment.domain.model.Payment
import com.payly.payment.infrastructure.persistence.entity.PaymentJpaEntity
import com.payly.payment.infrastructure.web.dto.response.PaymentResponse
import org.mapstruct.Mapper

@Mapper(componentModel = "spring")
interface PaymentMapper {

    fun toModel(entity: PaymentJpaEntity): Payment

    fun toEntity(model: Payment): PaymentJpaEntity

    fun toResponse(model: Payment): PaymentResponse
}
