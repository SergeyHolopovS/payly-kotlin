package com.payly.outbox.application.usecase.create

data class CreateOutboxEventCommand(
    val aggregateId: String,
    val eventType: String,
    val payload: Map<String, String>?
)
