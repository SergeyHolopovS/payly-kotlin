package com.payly.outbox.application.usecase.create

import com.payly.outbox.domain.model.OutboxEvent
import com.payly.outbox.domain.repository.OutboxEventRepository
import org.springframework.stereotype.Component

@Component
class CreateOutboxEventUseCase(
    private val repository: OutboxEventRepository
) {

    fun execute(command: CreateOutboxEventCommand): OutboxEvent
        // Сохраняем новое событие в базу данных
        = repository.save(
            aggregateId = command.aggregateId,
            eventType = command.eventType,
            payload = command.payload,
        )

}