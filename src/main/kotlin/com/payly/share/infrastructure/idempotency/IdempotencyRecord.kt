package com.payly.share.infrastructure.idempotency

import org.springframework.http.ResponseEntity
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

data class IdempotencyRecord(
    val merchantId: UUID,
    val key: String,
    val requestHash: String,
    val responseStatus: Int,
    val responseBody: String,
    val createdAt: Instant = Instant.now(),
) {

    fun replay(requestHash: String, objectMapper: ObjectMapper): ResponseEntity<*> {
        if (this.requestHash != requestHash) {
            throw IdempotencyKeyReusedException()
        }

        return ResponseEntity.status(responseStatus)
            .header("Idempotent-Replayed", "true")
            .body(objectMapper.readTree(responseBody))
    }

}
