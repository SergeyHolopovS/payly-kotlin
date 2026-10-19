package com.payly.share.infrastructure.idempotency

import com.payly.share.application.DistributedLock
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import kotlin.Long
import kotlin.String


@Component
class RedisDistributedLock(
    private val redis: StringRedisTemplate,
) : DistributedLock {

    override fun tryAcquire(key: String, ttl: Duration): DistributedLock.Lease? {
        val token = UUID.randomUUID().toString()
        val acquired = redis.opsForValue().setIfAbsent(key, token, ttl) == true   // SET NX PX
        return if (acquired) {
            DistributedLock.Lease { redis.execute(RELEASE, listOf(key), token) }
        } else null
    }

    private companion object {
        val RELEASE: RedisScript<Long> = RedisScript.of(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}