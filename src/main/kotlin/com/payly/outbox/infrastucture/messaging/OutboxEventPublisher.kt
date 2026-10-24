package com.payly.outbox.infrastucture.messaging

import com.payly.outbox.domain.model.OutboxEvent
import com.payly.outbox.domain.repository.OutboxEventRepository
import com.payly.outbox.infrastucture.properties.OutboxPublisherProperties
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.Duration
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
    private val properties: OutboxPublisherProperties,
    private val meterRegistry: MeterRegistry,
) {

    init {
        // Главный сигнал для алерта "outbox застрял": растущий бэклог значит, что Kafka
        // недоступна или publisher не успевает. Значение считается запросом в БД
        // на каждый scrape Prometheus (раз в 15–30 с) — count по индексу published_at дешёвый
        Gauge.builder("payly.outbox.events.pending", repository) { it.countUnpublished().toDouble() }
            .description("Outbox-события, ещё не опубликованные в Kafka")
            .register(meterRegistry)
    }

    @Scheduled(fixedDelayString = "\${outbox.publisher.poll-interval-ms:5000}")
    fun publishPending() {
        repository.findUnpublished(properties.batchSize).forEach(::publish)
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
        kafkaTemplate.send(properties.topic, event.aggregateId, serializedMessage)
            .whenComplete { _, exception ->
                if (exception == null) {
                    val publishedAt = Instant.now()
                    repository.markPublished(event.id, publishedAt)
                    recordPublished(event, "success")
                    recordLag(event, publishedAt)
                } else {
                    log.error("Failed to publish outbox event {} ({})", event.id, event.eventType, exception)
                    recordPublished(event, "failure")
                }
            }
    }

    private fun recordPublished(event: OutboxEvent, result: String) {
        Counter.builder("payly.outbox.events.published")
            .description("Попытки публикации outbox-событий в Kafka")
            .tag("event_type", event.eventType)
            .tag("result", result)
            .register(meterRegistry)
            .increment()
    }

    // Задержка от записи события в БД до подтверждения Kafka — то, насколько
    // консьюмеры отстают от реальности (включая интервал опроса и ретраи)
    private fun recordLag(event: OutboxEvent, publishedAt: Instant) {
        Timer.builder("payly.outbox.events.lag")
            .description("Время от создания outbox-события до публикации в Kafka")
            .tag("event_type", event.eventType)
            .register(meterRegistry)
            .record(Duration.between(event.createdAt, publishedAt))
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutboxEventPublisher::class.java)
    }

}
