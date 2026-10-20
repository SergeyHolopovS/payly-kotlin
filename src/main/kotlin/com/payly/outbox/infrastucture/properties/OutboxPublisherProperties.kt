package com.payly.outbox.infrastucture.properties

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "outbox.publisher")
data class OutboxPublisherProperties(

    // Kafka-топик, в который публикуются outbox-события.
    @field:NotBlank
    val topic: String = "payment.events",

    // Размер пачки, вычитываемой из outbox_events за один цикл публикации.
    @field:Min(1)
    val batchSize: Int = 100,

    // Интервал опроса outbox_events планировщиком, мс.
    @field:Min(100)
    val pollIntervalMs: Long = 5_000,

)
