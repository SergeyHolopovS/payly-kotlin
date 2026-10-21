package com.payly.unit.payment

import com.payly.outbox.application.usecase.create.CreateOutboxEventCommand
import com.payly.outbox.application.usecase.create.CreateOutboxEventUseCase
import com.payly.outbox.domain.model.OutboxEventType
import com.payly.payment.application.usecase.create.CreatePaymentCommand
import com.payly.payment.application.usecase.create.CreatePaymentUseCase
import com.payly.payment.domain.gateway.PaymentGateway
import com.payly.payment.domain.gateway.PaymentGatewayResult
import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentDeclineReason
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.domain.repository.PaymentRepository
import com.payly.support.UnitTest
import com.payly.support.data.TestMerchants
import com.payly.support.fixtures.TestCards
import com.payly.support.fixtures.aPayment
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Unit-тесты [CreatePaymentUseCase]: проверяем только логику use case'а —
 * какие статусы проставляются, что уходит в репозиторий и outbox и в каком
 * порядке относительно транзакций. БД, Spring и HTTP здесь нет: все
 * зависимости — Mockito-моки.
 */
@UnitTest
class CreatePaymentUseCaseTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    @Mock
    private lateinit var createOutbox: CreateOutboxEventUseCase

    @Mock
    private lateinit var paymentGateway: PaymentGateway

    // Настоящий TransactionTemplate поверх мока менеджера транзакций:
    // колбэки реально выполняются, а по вызовам менеджера видно границы транзакций
    @Mock
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var useCase: CreatePaymentUseCase

    private val merchantId = UUID.fromString(TestMerchants.DEFAULT_MERCHANT_ID)

    // Одна и та же команда для всех тестов — различается только ответ гейтвея
    private val command = CreatePaymentCommand(
        merchantId = merchantId,
        amount = 10_000,
        currency = "RUB",
        description = "Order #42",
        metadata = mapOf("orderId" to "42"),
        cardNumber = TestCards.SUCCESS,
    )

    @BeforeEach
    fun setUp() {
        // Собираем use case руками, а не через @InjectMocks: так явно видно,
        // какие зависимости у него есть, и TransactionTemplate можно сделать настоящим
        useCase = CreatePaymentUseCase(
            paymentRepository = paymentRepository,
            createOutbox = createOutbox,
            paymentGateway = paymentGateway,
            transactionTemplate = TransactionTemplate(transactionManager),
        )

        // TransactionTemplate просит у менеджера статус транзакции — отдаём пустышку
        whenever(transactionManager.getTransaction(any())).thenReturn(SimpleTransactionStatus())

        // Репозиторий "сохраняет" платёж и возвращает его в статусе CREATED, как настоящий
        whenever(paymentRepository.save(any(), any(), any(), anyOrNull(), any(), any()))
            .thenReturn(aPayment(merchantId = merchantId, status = PaymentStatus.CREATED))

        // update возвращает ровно то, что ему передали, — так в результате use case'а
        // виден статус, который он сам проставил
        whenever(paymentRepository.update(any())).thenAnswer { it.getArgument<Payment>(0) }
    }

    @Test
    fun `одобренный гейтвеем платёж завершается в статусе SUCCEEDED`() {
        whenever(paymentGateway.charge(TestCards.SUCCESS)).thenReturn(PaymentGatewayResult.Approved)

        val payment = useCase.execute(command)

        assertThat(payment.status).isEqualTo(PaymentStatus.SUCCEEDED)
        assertThat(payment.failureReason).isNull()
    }

    @Test
    fun `отклонённый гейтвеем платёж завершается в статусе FAILED с кодом причины`() {
        whenever(paymentGateway.charge(TestCards.SUCCESS))
            .thenReturn(PaymentGatewayResult.Declined(PaymentDeclineReason.INSUFFICIENT_FUNDS))

        val payment = useCase.execute(command)

        assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
        // В платёж пишется код причины (строка для API), а не имя enum'а
        assertThat(payment.failureReason).isEqualTo(PaymentDeclineReason.INSUFFICIENT_FUNDS.code)
    }

    @Test
    fun `сохраняет только последние 4 цифры карты`() {
        whenever(paymentGateway.charge(TestCards.SUCCESS)).thenReturn(PaymentGatewayResult.Approved)

        useCase.execute(command)

        // Полный номер карты уходит только в гейтвей; в репозиторий — только last4
        verify(paymentRepository).save(
            merchantId = merchantId,
            amount = 10_000,
            currency = "RUB",
            description = "Order #42",
            metadata = mapOf("orderId" to "42"),
            cardLast4 = "4242",
        )
    }

    @Test
    fun `пишет outbox-событие PAYMENT_CREATED по платежу в статусе PROCESSING`() {
        whenever(paymentGateway.charge(TestCards.SUCCESS)).thenReturn(PaymentGatewayResult.Approved)

        val payment = useCase.execute(command)

        // Событие создаётся в первой транзакции, ещё до похода в гейтвей,
        // поэтому в payload'е статус PROCESSING, а не финальный SUCCEEDED
        val captor = argumentCaptor<CreateOutboxEventCommand>()
        verify(createOutbox).execute(captor.capture())
        assertThat(captor.firstValue.aggregateId).isEqualTo(payment.id)
        assertThat(captor.firstValue.eventType).isEqualTo(OutboxEventType.PAYMENT_CREATED.name)
        assertThat(captor.firstValue.payload)
            .containsEntry("paymentId", payment.id)
            .containsEntry("merchantId", merchantId.toString())
            .containsEntry("status", PaymentStatus.PROCESSING.name)
    }

    @Test
    fun `ходит в гейтвей между двумя транзакциями, а не внутри них`() {
        whenever(paymentGateway.charge(TestCards.SUCCESS)).thenReturn(PaymentGatewayResult.Approved)

        useCase.execute(command)

        // Главный инвариант use case'а: долгий синхронный вызов гейтвея не должен
        // держать открытую транзакцию и соединение с БД. Проверяем это порядком
        // вызовов: commit первой транзакции → charge → открытие второй
        inOrder(transactionManager, paymentRepository, createOutbox, paymentGateway) {
            // 1-я транзакция: создание, перевод в PROCESSING и outbox
            verify(transactionManager).getTransaction(any())
            verify(paymentRepository).save(any(), any(), any(), anyOrNull(), any(), any())
            verify(paymentRepository).update(any())
            verify(createOutbox).execute(any())
            verify(transactionManager).commit(any())

            // Гейтвей — вне транзакции
            verify(paymentGateway).charge(TestCards.SUCCESS)

            // 2-я транзакция: финализация
            verify(transactionManager).getTransaction(any())
            verify(paymentRepository).update(any())
            verify(transactionManager).commit(any())
        }
    }

    @Test
    fun `при ошибке гейтвея платёж остаётся в PROCESSING и не финализируется`() {
        whenever(paymentGateway.charge(TestCards.SUCCESS)).thenThrow(IllegalStateException("gateway is down"))

        // Исключение гейтвея не глотается — его увидит GlobalExceptionHandler
        assertThatThrownBy { useCase.execute(command) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("gateway is down")

        // Был только один update — перевод в PROCESSING; финализации не было.
        // Такой платёж потом доведёт до финального статуса PaymentProcessingScheduler
        val captor = argumentCaptor<Payment>()
        verify(paymentRepository, times(1)).update(captor.capture())
        assertThat(captor.firstValue.status).isEqualTo(PaymentStatus.PROCESSING)

        // Вторая транзакция даже не открывалась
        verify(transactionManager, times(1)).getTransaction(any())
    }

}
