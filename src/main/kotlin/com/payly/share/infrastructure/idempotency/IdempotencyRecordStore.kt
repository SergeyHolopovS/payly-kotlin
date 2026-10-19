package com.payly.share.infrastructure.idempotency

import org.springframework.http.ResponseEntity
import java.util.UUID

interface IdempotencyRecordStore {

    fun find(merchantId: UUID, key: String): IdempotencyRecord?

    fun save(merchantId: UUID, key: String, requestHash: String, response: ResponseEntity<*>)

}
