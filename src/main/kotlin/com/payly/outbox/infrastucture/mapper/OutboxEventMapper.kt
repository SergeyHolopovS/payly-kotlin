package com.payly.outbox.infrastucture.mapper

import com.payly.outbox.domain.model.OutboxEvent
import com.payly.outbox.infrastucture.persistence.entity.OutboxEventJpaEntity
import org.mapstruct.Mapper

@Mapper(componentModel = "spring")
interface OutboxEventMapper {

    fun toModel(entity: OutboxEventJpaEntity): OutboxEvent

    fun toEntity(model: OutboxEvent): OutboxEventJpaEntity
}
