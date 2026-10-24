package com.payly.unit.outbox

import com.payly.outbox.application.usecase.create.CreateOutboxEventCommand
import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEvent
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.outbox.domain.repository.OutboxEventRepository
import com.payly.support.UnitTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit-тесты [CreateOutboxEventUseCase]: событие сохраняется в outbox ещё
 * не опубликованным и как есть, публикацией занимается OutboxEventPublisher.
 */
@UnitTest
class CreateOutboxEventUseCaseTest {

    @Mock
    private lateinit var repository: OutboxEventRepository

    private lateinit var useCase: CreateOutboxEventUseCase

    @BeforeEach
    fun setUp() {
        useCase = CreateOutboxEventUseCase(repository)
    }

    @Test
    fun `сохраняет событие с переданными aggregateId, типом и payload`() {
        val payload = mapOf("paymentId" to "pay_1", "status" to "SUCCEEDED")
        val saved = OutboxEvent(
            id = UUID.randomUUID(),
            aggregateId = "pay_1",
            eventType = OutboxEventType.PAYMENT_SUCCEEDED.name,
            payload = payload,
        )
        whenever(repository.save("pay_1", OutboxEventType.PAYMENT_SUCCEEDED.name, payload)).thenReturn(saved)

        val result = useCase.execute(
            CreateOutboxEventCommand(
                aggregateId = "pay_1",
                eventType = OutboxEventType.PAYMENT_SUCCEEDED.name,
                payload = payload,
            )
        )

        // Use case возвращает то, что вернул репозиторий (с id и createdAt из БД)
        assertThat(result).isEqualTo(saved)
        assertThat(result.publishedAt).isNull()
    }

    @Test
    fun `событие без payload сохраняется с null, а не с пустой картой`() {
        whenever(repository.save("pay_1", OutboxEventType.PAYMENT_CREATED.name, null)).thenReturn(
            OutboxEvent(id = UUID.randomUUID(), aggregateId = "pay_1", eventType = OutboxEventType.PAYMENT_CREATED.name)
        )

        useCase.execute(CreateOutboxEventCommand("pay_1", OutboxEventType.PAYMENT_CREATED.name, null))

        verify(repository).save("pay_1", OutboxEventType.PAYMENT_CREATED.name, null)
    }

}
