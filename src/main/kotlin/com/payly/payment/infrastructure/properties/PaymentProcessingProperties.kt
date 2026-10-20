package com.payly.payment.infrastructure.properties

import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "payment.processing")
data class PaymentProcessingProperties(

    // Нижняя граница случайной задержки обработки, сек. По умолчанию 1.
    @field:Min(0)
    val minDelaySeconds: Int = 1,

    // Верхняя граница случайной задержки обработки, сек. По умолчанию 5.
    @field:Min(0)
    val maxDelaySeconds: Int = 5,

    // Размер пачки, забираемой планировщиком за один SELECT ... FOR UPDATE SKIP LOCKED.
    @field:Min(1)
    val batchSize: Int = 100,

    // Интервал опроса планировщика, мс → payment.processing.poll-interval в @Scheduled.
    @field:Min(100)
    val pollIntervalMs: Long = 1000,
) {
    init {
        require(minDelaySeconds <= maxDelaySeconds) {
            "minDelaySeconds ($minDelaySeconds) должен быть <= maxDelaySeconds ($maxDelaySeconds)"
        }
    }
}