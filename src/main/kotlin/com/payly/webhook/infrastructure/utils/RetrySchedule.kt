package com.payly.webhook.infrastructure.utils

import java.time.Duration
import java.time.Instant
import kotlin.random.Random

object RetrySchedule {

    // индекс = номер попытки (0 = первая отправка уже сделана)
    private val delays = listOf(
        Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30),
        Duration.ofHours(2), Duration.ofHours(5), Duration.ofHours(10), Duration.ofHours(24)
    )
    const val MAX_ATTEMPTS = 8 // первая + 7 повторов

    fun nextAttemptAt(attempts: Int): Instant? {
        val idx = attempts - 1
        if (idx >= delays.size) return null // исчерпаны ретраи -> FAILED
        val base = delays[idx]
        val jitterMs = (base.toMillis() * 0.2 * Random.nextDouble(-1.0, 1.0)).toLong()
        return Instant.now().plusMillis(base.toMillis() + jitterMs)
    }

}