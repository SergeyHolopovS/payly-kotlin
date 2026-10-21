package com.payly.unit.refund

import com.payly.outbox.application.usecase.create.CreateOutboxEventCommand
import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.domain.repository.PaymentRepository
import com.payly.refund.application.usecase.create.CreateRefundCommand
import com.payly.refund.application.usecase.create.CreateRefundUseCase
import com.payly.refund.domain.model.Refund
import com.payly.refund.domain.model.RefundStatus
import com.payly.refund.domain.repository.RefundRepository
import com.payly.share.infrastructure.exceptions.payment.PaymentNotFoundException
import com.payly.share.infrastructure.exceptions.refund.InvalidRefundAmountException
import com.payly.share.infrastructure.exceptions.refund.PaymentNotRefundableException
import com.payly.support.UnitTest
import com.payly.support.data.TestMerchants
import com.payly.support.fixtures.aPayment
import com.payly.support.fixtures.aRefund
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit-тесты [CreateRefundUseCase]: из каких статусов можно вернуть деньги,
 * какую сумму, как меняется платёж и что уходит в outbox. Репозитории и outbox —
 * Mockito-моки; транзакционность (@Transactional) и конкурентные возвраты
 * проверяются интеграционными тестами.
 */
@UnitTest
class CreateRefundUseCaseTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var refundRepository: RefundRepository

    @Mock
    private lateinit var outbox: CreateOutboxEventUseCase

    private lateinit var useCase: CreateRefundUseCase

    private val ownerId = UUID.fromString(TestMerchants.DEFAULT_MERCHANT_ID)
    private val otherMerchantId = UUID.fromString(TestMerchants.OTHER_MERCHANT_ID)

    @BeforeEach
    fun setUp() {
        useCase = CreateRefundUseCase(
            paymentRepository = paymentRepository,
            refundRepository = refundRepository,
            outbox = outbox,
        )
    }

    // ---------- Полный и частичный возврат ----------

    @Test
    fun `без суммы возвращает весь платёж и переводит его в REFUNDED`() {
        givenPayment(aPayment(id = "pay_1", amount = 10_000, status = PaymentStatus.SUCCEEDED))
        givenRefundIsSaved()

        val refund = useCase.execute(CreateRefundCommand(merchantId = ownerId, paymentId = "pay_1"))

        // Возврат создаётся сразу успешным — асинхронного шага с гейтвеем пока нет
        assertThat(refund.amount).isEqualTo(10_000)
        assertThat(refund.status).isEqualTo(RefundStatus.SUCCEEDED)

        val payment = savedPayment()
        assertThat(payment.status).isEqualTo(PaymentStatus.REFUNDED)
        assertThat(payment.refundedAmount).isEqualTo(10_000)
    }

    @Test
    fun `частичный возврат переводит платёж в PARTIALLY_REFUNDED`() {
        givenPayment(aPayment(id = "pay_1", amount = 10_000, status = PaymentStatus.SUCCEEDED))
        givenRefundIsSaved()

        val refund = useCase.execute(CreateRefundCommand(merchantId = ownerId, paymentId = "pay_1", amount = 3_000))

        assertThat(refund.amount).isEqualTo(3_000)
        val payment = savedPayment()
        assertThat(payment.status).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED)
        assertThat(payment.refundedAmount).isEqualTo(3_000)
    }

    @Test
    fun `повторный частичный возврат оставляет платёж в PARTIALLY_REFUNDED и копит сумму`() {
        givenPayment(
            aPayment(id = "pay_1", amount = 10_000, status = PaymentStatus.PARTIALLY_REFUNDED)
                .copy(refundedAmount = 3_000)
        )
        givenRefundIsSaved()

        // PARTIALLY_REFUNDED → PARTIALLY_REFUNDED не является переходом по PaymentStatus,
        // use case обязан это учесть и не вызывать transitionTo (иначе было бы исключение)
        useCase.execute(CreateRefundCommand(merchantId = ownerId, paymentId = "pay_1", amount = 2_000))

        val payment = savedPayment()
        assertThat(payment.status).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED)
        assertThat(payment.refundedAmount).isEqualTo(5_000)
    }

    @Test
    fun `без суммы после частичного возврата возвращает остаток, а не всю сумму платежа`() {
        givenPayment(
            aPayment(id = "pay_1", amount = 10_000, status = PaymentStatus.PARTIALLY_REFUNDED)
                .copy(refundedAmount = 3_000)
        )
        givenRefundIsSaved()

        val refund = useCase.execute(CreateRefundCommand(merchantId = ownerId, paymentId = "pay_1"))

        // Иначе "верни всё" после частичного возврата всегда падало бы на лимите
        assertThat(refund.amount).isEqualTo(7_000)
        val payment = savedPayment()
        assertThat(payment.status).isEqualTo(PaymentStatus.REFUNDED)
        assertThat(payment.refundedAmount).isEqualTo(10_000)
    }

    @Test
    fun `пишет outbox-событие REFUND_SUCCEEDED по платежу`() {
        givenPayment(aPayment(id = "pay_1", amount = 10_000, status = PaymentStatus.SUCCEEDED))
        givenRefundIsSaved()

        val refund = useCase.execute(CreateRefundCommand(merchantId = ownerId, paymentId = "pay_1", amount = 4_000))

        // aggregateId — id ПЛАТЕЖА, а не возврата: все события одного платежа
        // попадают в одну партицию Kafka и приходят подписчику по порядку
        val captor = argumentCaptor<CreateOutboxEventCommand>()
        verify(outbox).execute(captor.capture())
        assertThat(captor.firstValue.aggregateId).isEqualTo("pay_1")
        assertThat(captor.firstValue.eventType).isEqualTo(OutboxEventType.REFUND_SUCCEEDED.name)
        assertThat(captor.firstValue.payload)
            .containsEntry("refundId", refund.id)
            .containsEntry("paymentId", "pay_1")
            .containsEntry("amount", "4000")
            .containsEntry("status", RefundStatus.SUCCEEDED.name)
    }

    // ---------- Некорректная сумма ----------

    @Test
    fun `сумма больше остатка отклоняется`() {
        givenPayment(
            aPayment(id = "pay_1", amount = 10_000, status = PaymentStatus.PARTIALLY_REFUNDED)
                .copy(refundedAmount = 8_000)
        )

        // Остаток 2_000 — вернуть 2_001 нельзя
        assertThatThrownBy {
            useCase.execute(CreateRefundCommand(merchantId = ownerId, paymentId = "pay_1", amount = 2_001))
        }.isInstanceOf(InvalidRefundAmountException::class.java)

        verifyNothingChanged()
    }

    // На уровне API ноль и отрицательные суммы отсекает @Positive, но use case
    // не должен полагаться на то, что его всегда вызывают через контроллер
    @ParameterizedTest
    @ValueSource(longs = [0, -1, -10_000])
    fun `неположительная сумма отклоняется`(amount: Long) {
        givenPayment(aPayment(id = "pay_1", amount = 10_000, status = PaymentStatus.SUCCEEDED))

        assertThatThrownBy {
            useCase.execute(CreateRefundCommand(merchantId = ownerId, paymentId = "pay_1", amount = amount))
        }.isInstanceOf(InvalidRefundAmountException::class.java)

        verifyNothingChanged()
    }

    // ---------- Неподходящий платёж ----------

    // Вернуть можно только списанные и ещё не полностью возвращённые деньги.
    // PROCESSING тоже здесь: результат списания ещё неизвестен
    @ParameterizedTest
    @EnumSource(value = PaymentStatus::class, names = ["SUCCEEDED", "PARTIALLY_REFUNDED"], mode = EnumSource.Mode.EXCLUDE)
    fun `платёж в неподходящем статусе не возвращается`(status: PaymentStatus) {
        givenPayment(aPayment(id = "pay_1", status = status))

        assertThatThrownBy { useCase.execute(CreateRefundCommand(merchantId = ownerId, paymentId = "pay_1")) }
            .isInstanceOf(PaymentNotRefundableException::class.java)
            .hasFieldOrPropertyWithValue("paymentStatus", status)

        verifyNothingChanged()
    }

    @Test
    fun `чужой платёж выглядит как несуществующий`() {
        givenPayment(aPayment(id = "pay_1", merchantId = ownerId, status = PaymentStatus.SUCCEEDED))

        assertThatThrownBy { useCase.execute(CreateRefundCommand(merchantId = otherMerchantId, paymentId = "pay_1")) }
            .isInstanceOf(PaymentNotFoundException::class.java)

        verifyNothingChanged()
    }

    @Test
    fun `отсутствующий платёж пробрасывает PaymentNotFoundException из репозитория`() {
        whenever(paymentRepository.getById("pay_missing")).thenThrow(PaymentNotFoundException())

        assertThatThrownBy { useCase.execute(CreateRefundCommand(merchantId = ownerId, paymentId = "pay_missing")) }
            .isInstanceOf(PaymentNotFoundException::class.java)

        verifyNothingChanged()
    }

    // ---------- Хелперы ----------

    /** Репозиторий находит платёж по id; владелец по умолчанию — ownerId. */
    private fun givenPayment(payment: Payment) {
        whenever(paymentRepository.getById(payment.id)).thenReturn(payment)
    }

    /**
     * RefundRepository ведёт себя как настоящий: save создаёт возврат в PENDING
     * с переданными paymentId и суммой, update возвращает переданную модель.
     */
    private fun givenRefundIsSaved() {
        whenever(refundRepository.save(any(), any())).thenAnswer {
            aRefund(id = "re_1", paymentId = it.getArgument(0), amount = it.getArgument(1))
        }
        whenever(refundRepository.update(any())).thenAnswer { it.getArgument<Refund>(0) }
    }

    /** Платёж, который use case отправил на сохранение. */
    private fun savedPayment(): Payment {
        val captor = argumentCaptor<Payment>()
        verify(paymentRepository).update(captor.capture())
        return captor.firstValue
    }

    /** Ни возврат, ни платёж не сохранялись, событий нет. */
    private fun verifyNothingChanged() {
        verify(refundRepository, never()).save(any(), any())
        verify(paymentRepository, never()).update(any())
        verify(outbox, never()).execute(any())
    }

}
