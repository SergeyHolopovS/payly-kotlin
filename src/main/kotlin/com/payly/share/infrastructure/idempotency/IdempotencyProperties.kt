package com.payly.share.infrastructure.idempotency

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("payment-gateway.idempotency")
data class IdempotencyProperties(
    val lockTtl: Duration = Duration.ofSeconds(30),
    val recordTtl: Duration = Duration.ofHours(24),
)