package com.payly.refund.infrastructure.mapper

import com.payly.refund.domain.model.Refund
import com.payly.refund.infrastructure.persistence.entity.RefundJpaEntity
import com.payly.refund.infrastructure.web.dto.response.RefundResponse
import org.mapstruct.Mapper

@Mapper(componentModel = "spring")
interface RefundMapper {

    fun toModel(entity: RefundJpaEntity): Refund

    fun toEntity(model: Refund): RefundJpaEntity

    fun toResponse(model: Refund): RefundResponse

}
