package com.payly.share.infrastructure.idempotency

import com.payly.share.application.DistributedLock
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service
class IdempotencyService(
    private val lock: DistributedLock,
    private val store: IdempotencyRecordStore,   // Redis + PostgreSQL
    private val props: IdempotencyProperties,
    private val objectMapper: ObjectMapper,
) {
    fun execute(
        merchantId: UUID,
        key: String,
        requestHash: String,
        operation: () -> ResponseEntity<*>,
    ): ResponseEntity<*> {
        val lease = lock.tryAcquire("idem-lock:$merchantId:$key", props.lockTtl)
            ?: throw IdempotencyRequestInProgressException()                     // 409

        return lease.use {
            store.find(merchantId, key)
                ?.replay(requestHash, objectMapper)        // 422 при чужом хэше, иначе ответ + Idempotent-Replayed
                ?: operation().also { response ->
                    if (!response.statusCode.is5xxServerError) {
                        store.save(merchantId, key, requestHash, response)
                    }
                }
        }
    }
}