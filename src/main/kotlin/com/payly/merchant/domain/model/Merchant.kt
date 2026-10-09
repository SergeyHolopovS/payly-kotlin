package com.payly.merchant.domain.model

import java.time.Instant
import java.util.UUID

data class Merchant(
    val id: UUID,
    val name: String,
    val apiKeyHash: String,
    val webhookUrl: String,
    val webhookSecret: String,
    val createdAt: Instant,
)
