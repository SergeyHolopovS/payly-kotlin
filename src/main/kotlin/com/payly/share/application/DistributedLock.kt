package com.payly.share.application

import java.lang.AutoCloseable
import java.time.Duration

interface DistributedLock {
    fun tryAcquire(key: String, ttl: Duration): Lease?

    fun interface Lease : AutoCloseable {
        override fun close()
    }
}