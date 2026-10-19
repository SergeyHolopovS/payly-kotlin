package com.payly.outbox.infrastucture.schedulers

import com.payly.outbox.domain.model.OutboxEvent
import com.payly.outbox.domain.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class OutboxKafkaWorker(
    private val repository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(OutboxKafkaWorker::class.java)

    @Scheduled(fixedDelay = 5000)
    fun publishUnpublishedEvents() {
        val events = repository.findUnpublished(BATCH_SIZE)
        events.forEach(::publish)
    }

    private fun publish(event: OutboxEvent) {
        kafkaTemplate.send(event.eventType, event.aggregateId, objectMapper.writeValueAsString(event))
            .whenComplete { _: SendResult<String, String>?, exception: Throwable? ->
                if (exception == null) {
                    repository.markAsPublished(event.id)
                } else {
                    log.error("Failed to publish outbox event {}", event.id, exception)
                }
            }
    }

    companion object {
        private const val BATCH_SIZE = 100
    }

}
