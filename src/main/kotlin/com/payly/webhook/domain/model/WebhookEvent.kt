package com.payly.webhook.domain.model

import java.time.Instant

data class WebhookEvent(
    val id: String,
    val type: String,
    val createdAt: Instant,
    val data: WebhookEventData
)
