package com.payly.payment.infrastructure.mapper

import com.payly.payment.domain.model.Payment
import com.payly.payment.infrastructure.persistence.entity.PaymentJpaEntity
import com.payly.payment.infrastructure.web.dto.response.PaymentResponse
import org.mapstruct.Mapper
import org.mapstruct.Mapping

@Mapper(componentModel = "spring")
interface PaymentMapper {

    @Mapping(target = "transitionTo", ignore = true)
    fun toModel(entity: PaymentJpaEntity): Payment

    fun toEntity(model: Payment): PaymentJpaEntity

    fun toResponse(model: Payment): PaymentResponse
}
