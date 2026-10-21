package com.payly.unit.payment

import com.payly.payment.application.usecase.get.GetPaymentCommand
import com.payly.payment.application.usecase.get.GetPaymentUseCase
import com.payly.payment.domain.repository.PaymentRepository
import com.payly.share.infrastructure.exceptions.payment.PaymentNotFoundException
import com.payly.support.UnitTest
import com.payly.support.data.TestMerchants
import com.payly.support.fixtures.aPayment
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit-тесты [GetPaymentUseCase]: платёж ищется по id, но отдаётся только
 * тому продавцу, которому он принадлежит. Репозиторий — Mockito-мок.
 */
@UnitTest
class GetPaymentUseCaseTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    private lateinit var useCase: GetPaymentUseCase

    private val ownerId = UUID.fromString(TestMerchants.DEFAULT_MERCHANT_ID)
    private val otherMerchantId = UUID.fromString(TestMerchants.OTHER_MERCHANT_ID)

    @BeforeEach
    fun setUp() {
        useCase = GetPaymentUseCase(repository = paymentRepository)
    }

    @Test
    fun `возвращает платёж его владельцу`() {
        val payment = aPayment(id = "pay_1", merchantId = ownerId)
        whenever(paymentRepository.getById("pay_1")).thenReturn(payment)

        val result = useCase.execute(GetPaymentCommand(id = "pay_1", merchantId = ownerId))

        // Use case ничего не меняет в платеже — возвращает ровно то, что дал репозиторий
        assertThat(result).isEqualTo(payment)
    }

    @Test
    fun `чужой платёж выглядит как несуществующий`() {
        whenever(paymentRepository.getById("pay_1")).thenReturn(aPayment(id = "pay_1", merchantId = ownerId))

        // Именно 404, а не 403: иначе по ответу можно было бы перебором
        // выяснять, какие id платежей существуют у других продавцов
        assertThatThrownBy { useCase.execute(GetPaymentCommand(id = "pay_1", merchantId = otherMerchantId)) }
            .isInstanceOf(PaymentNotFoundException::class.java)
    }

    @Test
    fun `отсутствующий платёж пробрасывает PaymentNotFoundException из репозитория`() {
        // "Не найдено" определяет репозиторий — use case его не перехватывает
        whenever(paymentRepository.getById("pay_missing")).thenThrow(PaymentNotFoundException())

        assertThatThrownBy { useCase.execute(GetPaymentCommand(id = "pay_missing", merchantId = ownerId)) }
            .isInstanceOf(PaymentNotFoundException::class.java)
    }

}
