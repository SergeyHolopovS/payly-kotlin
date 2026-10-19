package com.payly.share.infrastructure.idempotency

import com.payly.share.infrastructure.idempotency.persistence.entity.IdempotencyRecordJpaEntity
import com.payly.share.infrastructure.idempotency.persistence.repository.IdempotencyRecordJpaRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Component
class IdempotencyRecordStoreImpl(
    private val redis: StringRedisTemplate,
    private val jpaRepository: IdempotencyRecordJpaRepository,
    private val objectMapper: ObjectMapper,
    private val props: IdempotencyProperties,
) : IdempotencyRecordStore {

    override fun find(merchantId: UUID, key: String): IdempotencyRecord? {
        val cached = redis.opsForValue().get(redisKey(merchantId, key))
        if (cached != null) {
            return objectMapper.readValue(cached, IdempotencyRecord::class.java)
        }

        val persisted = jpaRepository.findByMerchantIdAndKey(merchantId, key)
            ?: return null

        return persisted.toDomain().also(::cache)
    }

    override fun save(merchantId: UUID, key: String, requestHash: String, response: ResponseEntity<*>) {
        val record = IdempotencyRecord(
            merchantId = merchantId,
            key = key,
            requestHash = requestHash,
            responseStatus = response.statusCode.value(),
            responseBody = objectMapper.writeValueAsString(response.body),
        )

        jpaRepository.save(
            IdempotencyRecordJpaEntity(
                merchantId = record.merchantId,
                key = record.key,
                requestHash = record.requestHash,
                responseStatus = record.responseStatus,
                responseBody = record.responseBody,
            )
        )
        cache(record)
    }

    private fun cache(record: IdempotencyRecord) {
        redis.opsForValue().set(
            redisKey(record.merchantId, record.key),
            objectMapper.writeValueAsString(record),
            props.recordTtl,
        )
    }

    private fun IdempotencyRecordJpaEntity.toDomain() = IdempotencyRecord(
        merchantId = merchantId,
        key = key,
        requestHash = requestHash,
        responseStatus = responseStatus,
        responseBody = responseBody,
        createdAt = createdAt,
    )

    private fun redisKey(merchantId: UUID, key: String) = "idem-record:$merchantId:$key"

}
