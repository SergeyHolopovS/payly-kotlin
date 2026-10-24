package com.payly.unit.idempotency

import com.payly.share.application.DistributedLock
import com.payly.share.infrastructure.idempotency.IdempotencyKeyReusedException
import com.payly.share.infrastructure.idempotency.IdempotencyProperties
import com.payly.share.infrastructure.idempotency.IdempotencyRecord
import com.payly.share.infrastructure.idempotency.IdempotencyRecordStore
import com.payly.share.infrastructure.idempotency.IdempotencyRequestInProgressException
import com.payly.share.infrastructure.idempotency.IdempotencyService
import com.payly.support.UnitTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration
import java.util.UUID

/**
 * Unit-тесты [IdempotencyService]: блокировка по ключу, повтор сохранённого ответа
 * и какие ответы запоминаются. Лок и хранилище — моки.
 */
@UnitTest
class IdempotencyServiceTest {

    @Mock
    private lateinit var lock: DistributedLock

    @Mock
    private lateinit var store: IdempotencyRecordStore

    private val objectMapper = jacksonObjectMapper()

    private val props = IdempotencyProperties(lockTtl = Duration.ofSeconds(5))

    private lateinit var service: IdempotencyService

    private val merchantId = UUID.randomUUID()
    private val key = "key-1"
    private val hash = "hash-1"

    private var leaseClosed = false

    @BeforeEach
    fun setUp() {
        service = IdempotencyService(lock, store, props, objectMapper)
    }

    @Test
    fun `первый запрос выполняет операцию и сохраняет ответ`() {
        givenLockAcquired()
        val response = ResponseEntity.status(HttpStatus.CREATED).body(mapOf("id" to "pay_1"))

        val result = service.execute(merchantId, key, hash) { response }

        assertThat(result).isSameAs(response)
        verify(store).save(merchantId, key, hash, response)
        assertThat(leaseClosed).isTrue()
    }

    @Test
    fun `лок берётся по продавцу и ключу с TTL из настроек`() {
        givenLockAcquired()

        service.execute(merchantId, key, hash) { ResponseEntity.ok("{}") }

        verify(lock).tryAcquire("idem-lock:$merchantId:$key", Duration.ofSeconds(5))
    }

    @Test
    fun `повтор с тем же хэшем возвращает сохранённый ответ без выполнения операции`() {
        givenLockAcquired()
        whenever(store.find(merchantId, key)).thenReturn(aRecord(requestHash = hash))
        var executed = false

        val result = service.execute(merchantId, key, hash) { executed = true; ResponseEntity.ok("{}") }

        assertThat(executed).isFalse()
        assertThat(result.statusCode.value()).isEqualTo(201)
        assertThat(result.headers.getFirst("Idempotent-Replayed")).isEqualTo("true")
        assertThat(objectMapper.writeValueAsString(result.body)).isEqualTo("""{"id":"pay_1"}""")
        verify(store, never()).save(any(), any(), any(), any())
    }

    @Test
    fun `тот же ключ с другим телом запроса — ошибка, лок отпускается`() {
        givenLockAcquired()
        whenever(store.find(merchantId, key)).thenReturn(aRecord(requestHash = "other-hash"))

        assertThatThrownBy { service.execute(merchantId, key, hash) { ResponseEntity.ok("{}") } }
            .isInstanceOf(IdempotencyKeyReusedException::class.java)
        assertThat(leaseClosed).isTrue()
    }

    @Test
    fun `лок занят параллельным запросом — 409 без выполнения операции`() {
        whenever(lock.tryAcquire(any(), any())).thenReturn(null)
        var executed = false

        assertThatThrownBy { service.execute(merchantId, key, hash) { executed = true; ResponseEntity.ok("{}") } }
            .isInstanceOf(IdempotencyRequestInProgressException::class.java)
        assertThat(executed).isFalse()
    }

    @Test
    fun `ответ 5xx не запоминается — клиент может повторить запрос`() {
        givenLockAcquired()

        service.execute(merchantId, key, hash) { ResponseEntity.internalServerError().body("{}") }

        verify(store, never()).save(any(), any(), any(), any())
    }

    @Test
    fun `ответ 4xx запоминается — повтор не изменит результат`() {
        givenLockAcquired()
        val response = ResponseEntity.badRequest().body("{}")

        service.execute(merchantId, key, hash) { response }

        verify(store).save(eq(merchantId), eq(key), eq(hash), eq(response))
    }

    @Test
    fun `исключение операции не сохраняется и лок отпускается`() {
        givenLockAcquired()

        assertThatThrownBy { service.execute(merchantId, key, hash) { throw IllegalStateException("boom") } }
            .isInstanceOf(IllegalStateException::class.java)
        verify(store, never()).save(any(), any(), any(), any())
        assertThat(leaseClosed).isTrue()
    }

    private fun givenLockAcquired() {
        whenever(lock.tryAcquire(any(), any())).thenReturn(DistributedLock.Lease { leaseClosed = true })
    }

    private fun aRecord(requestHash: String) = IdempotencyRecord(
        merchantId = merchantId,
        key = key,
        requestHash = requestHash,
        responseStatus = 201,
        responseBody = """{"id":"pay_1"}""",
    )

}
