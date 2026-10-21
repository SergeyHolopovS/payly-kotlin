package com.payly.unit.payment

import com.payly.outbox.application.usecase.create.CreateOutboxEventCommand
import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.application.usecase.cancel.CancelPaymentCommand
import com.payly.payment.application.usecase.cancel.CancelPaymentUseCase
import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.domain.repository.PaymentRepository
import com.payly.share.infrastructure.exceptions.payment.InvalidPaymentStatusTransitionException
import com.payly.share.infrastructure.exceptions.payment.PaymentNotFoundException
import com.payly.share.infrastructure.exceptions.payment.PaymentProcessingException
import com.payly.support.UnitTest
import com.payly.support.data.TestMerchants
import com.payly.support.fixtures.aPayment
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit-тесты [CancelPaymentUseCase]: кто может отменить платёж, из каких статусов,
 * что сохраняется и какое событие уходит в outbox. Транзакционность (@Transactional)
 * здесь не проверяется — без Spring её нет; откат проверяет интеграционный тест.
 */
@UnitTest
class CancelPaymentUseCaseTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var outbox: CreateOutboxEventUseCase

    private lateinit var useCase: CancelPaymentUseCase

    private val ownerId = UUID.fromString(TestMerchants.DEFAULT_MERCHANT_ID)
    private val otherMerchantId = UUID.fromString(TestMerchants.OTHER_MERCHANT_ID)

    @BeforeEach
    fun setUp() {
        useCase = CancelPaymentUseCase(repository = paymentRepository, outbox = outbox)
    }

    // ---------- Успешная отмена ----------

    // CREATED — единственный статус, из которого PaymentStatus разрешает CANCELED:
    // платёж записан, но в гейтвей за ним ещё не ходили
    @Test
    fun `отменяет платёж, по которому ещё не ходили в гейтвей`() {
        givenPayment(aPayment(id = "pay_1", merchantId = ownerId, status = PaymentStatus.CREATED))

        val result = useCase.execute(CancelPaymentCommand(merchantId = ownerId, id = "pay_1"))

        assertThat(result.status).isEqualTo(PaymentStatus.CANCELED)

        // В репозиторий уходит тот же платёж, но уже в CANCELED
        val captor = argumentCaptor<Payment>()
        verify(paymentRepository).update(captor.capture())
        assertThat(captor.firstValue.id).isEqualTo("pay_1")
        assertThat(captor.firstValue.status).isEqualTo(PaymentStatus.CANCELED)
    }

    @Test
    fun `пишет outbox-событие PAYMENT_CANCELLED с итоговым статусом`() {
        givenPayment(aPayment(id = "pay_1", merchantId = ownerId, status = PaymentStatus.CREATED))

        useCase.execute(CancelPaymentCommand(merchantId = ownerId, id = "pay_1"))

        // В отличие от создания, здесь событие пишется ПОСЛЕ смены статуса,
        // поэтому в payload'е уже CANCELED
        val captor = argumentCaptor<CreateOutboxEventCommand>()
        verify(outbox).execute(captor.capture())
        assertThat(captor.firstValue.aggregateId).isEqualTo("pay_1")
        assertThat(captor.firstValue.eventType).isEqualTo(OutboxEventType.PAYMENT_CANCELLED.name)
        assertThat(captor.firstValue.payload).containsEntry("status", PaymentStatus.CANCELED.name)
    }

    @Test
    fun `сначала сохраняет платёж, потом пишет событие`() {
        givenPayment(aPayment(id = "pay_1", merchantId = ownerId, status = PaymentStatus.CREATED))

        useCase.execute(CancelPaymentCommand(merchantId = ownerId, id = "pay_1"))

        // Если update упадёт (например, на optimistic lock), событие не должно
        // успеть записаться — порядок вызовов это гарантирует
        inOrder(paymentRepository, outbox) {
            verify(paymentRepository).update(any())
            verify(outbox).execute(any())
        }
    }

    // ---------- Отказ в отмене ----------

    @Test
    fun `платёж в обработке гейтвеем не отменяется`() {
        givenPayment(aPayment(id = "pay_1", merchantId = ownerId, status = PaymentStatus.PROCESSING))

        // Отдельная ошибка (а не общий "недопустимый переход"): запрос в гейтвей
        // уже ушёл, остановить его нельзя — клиенту нужно дождаться результата
        assertThatThrownBy { useCase.execute(CancelPaymentCommand(merchantId = ownerId, id = "pay_1")) }
            .isInstanceOf(PaymentProcessingException::class.java)
            .hasFieldOrPropertyWithValue("code", "payment_processing")

        verify(paymentRepository, never()).update(any())
        verify(outbox, never()).execute(any())
    }

    // Всё, что уже прошло через гейтвей, отменить нельзя — для успешных
    // платежей для этого есть возврат (refund). PROCESSING проверяется отдельно выше
    @ParameterizedTest
    @EnumSource(value = PaymentStatus::class, names = ["CREATED", "PROCESSING"], mode = EnumSource.Mode.EXCLUDE)
    fun `не отменяет платёж в статусе, из которого отмена запрещена`(status: PaymentStatus) {
        givenPayment(aPayment(id = "pay_1", merchantId = ownerId, status = status))

        assertThatThrownBy { useCase.execute(CancelPaymentCommand(merchantId = ownerId, id = "pay_1")) }
            .isInstanceOf(InvalidPaymentStatusTransitionException::class.java)
            .hasFieldOrPropertyWithValue("from", status)
            .hasFieldOrPropertyWithValue("to", PaymentStatus.CANCELED)

        // Ничего не сохранено и никаких событий
        verify(paymentRepository, never()).update(any())
        verify(outbox, never()).execute(any())
    }

    @Test
    fun `чужой платёж выглядит как несуществующий и не отменяется`() {
        givenPayment(aPayment(id = "pay_1", merchantId = ownerId, status = PaymentStatus.CREATED))

        // Проверка владельца идёт раньше проверки статуса: посторонний не должен
        // по коду ошибки (404 или 409) узнать даже статус чужого платежа
        assertThatThrownBy { useCase.execute(CancelPaymentCommand(merchantId = otherMerchantId, id = "pay_1")) }
            .isInstanceOf(PaymentNotFoundException::class.java)

        verify(paymentRepository, never()).update(any())
        verify(outbox, never()).execute(any())
    }

    @Test
    fun `отсутствующий платёж пробрасывает PaymentNotFoundException из репозитория`() {
        whenever(paymentRepository.getById("pay_missing")).thenThrow(PaymentNotFoundException())

        assertThatThrownBy { useCase.execute(CancelPaymentCommand(merchantId = ownerId, id = "pay_missing")) }
            .isInstanceOf(PaymentNotFoundException::class.java)

        verify(outbox, never()).execute(any())
    }

    // ---------- Хелперы ----------

    /**
     * Репозиторий находит [payment] по id. Стабить update не нужно: use case
     * не использует его результат и возвращает модель, которую построил сам.
     */
    private fun givenPayment(payment: Payment) {
        whenever(paymentRepository.getById(payment.id)).thenReturn(payment)
    }

}
