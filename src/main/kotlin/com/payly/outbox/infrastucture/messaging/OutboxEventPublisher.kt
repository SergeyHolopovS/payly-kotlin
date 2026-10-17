package com.payly.outbox.infrastucture.messaging

import com.payly.outbox.domain.model.OutboxEvent
import com.payly.outbox.domain.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * Relay транзакционного outbox: периодически вычитывает ещё не опубликованные
 * записи из outbox_events и доставляет их в Kafka (polling publisher pattern).
 *
 * Запись в БД (Payment + OutboxEvent) и публикация в Kafka разделены по времени
 * намеренно — так гарантируется "at least once": если процесс упадёт между
 * отправкой в Kafka и отметкой publishedAt, событие будет отправлено повторно
 * при следующем цикле. Консьюмеры должны быть идемпотентны (ключ идемпотентности
 * — eventId).
 */
@Component
class OutboxEventPublisher(
    private val repository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {

    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    fun publishPending() {
        repository.findUnpublished(BATCH_SIZE).forEach(::publish)
    }

    private fun publish(event: OutboxEvent) {
        val message = OutboxEventMessage(
            eventId = event.id,
            eventType = event.eventType,
            aggregateId = event.aggregateId,
            payload = event.payload ?: emptyMap(),
            occurredAt = event.createdAt,
        )

        // Явный тип промежуточной переменной — чтобы Kotlin не выводил тип
        // через вложенный вызов двух Java-API с разными системами аннотаций
        // nullability (Jackson 3 / JSpecify и Spring); иначе компилятор
        // падает на KT-80247 (inaccessible UnknownNullability)
        val serializedMessage: String = objectMapper.writeValueAsString(message)

        // Ключ = aggregateId, чтобы все события одного платежа попадали
        // в одну партицию и консьюмер видел их в порядке создания
        kafkaTemplate.send(TOPIC, event.aggregateId, serializedMessage)
            .whenComplete { _, exception ->
                if (exception == null) {
                    repository.markPublished(event.id, Instant.now())
                } else {
                    log.error("Failed to publish outbox event {} ({})", event.id, event.eventType, exception)
                }
            }
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutboxEventPublisher::class.java)
        private const val TOPIC = "payment.events"
        private const val BATCH_SIZE = 100
        private const val POLL_INTERVAL_MS = 5_000L
    }

}
