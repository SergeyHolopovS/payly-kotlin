package com.payly.unit.payment

import com.payly.payment.application.usecase.list.GetListPaymentsCommand
import com.payly.payment.application.usecase.list.GetListPaymentsUseCase
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.domain.repository.PaymentRepository
import com.payly.support.UnitTest
import com.payly.support.data.TestMerchants
import com.payly.support.fixtures.aPayment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.UUID

/**
 * Unit-тесты [GetListPaymentsUseCase]. Сама фильтрация живёт в репозитории
 * (JPA Specifications) и проверяется интеграционными тестами; здесь проверяем,
 * что use case передаёт фильтры без искажений и не теряет пагинацию.
 */
@UnitTest
class GetListPaymentsUseCaseTest {

    @Mock
    private lateinit var paymentRepository: PaymentRepository

    private lateinit var useCase: GetListPaymentsUseCase

    private val merchantId = UUID.fromString(TestMerchants.DEFAULT_MERCHANT_ID)
    private val pageable = PageRequest.of(1, 10)

    @BeforeEach
    fun setUp() {
        useCase = GetListPaymentsUseCase(repository = paymentRepository)
    }

    @Test
    fun `передаёт все фильтры и пагинацию в репозиторий как есть`() {
        val from = Instant.parse("2026-01-01T00:00:00Z")
        val to = Instant.parse("2026-02-01T00:00:00Z")
        val page = PageImpl(listOf(aPayment(id = "pay_1")), pageable, 11)
        whenever(
            paymentRepository.getList(
                merchantId = merchantId,
                createdFrom = from,
                createdTo = to,
                status = PaymentStatus.SUCCEEDED,
                pageable = pageable,
            )
        ).thenReturn(page)

        val result = useCase.execute(
            GetListPaymentsCommand(
                merchantId = merchantId,
                createdFrom = from,
                createdTo = to,
                status = PaymentStatus.SUCCEEDED,
                pageable = pageable,
            )
        )

        // Страница возвращается вместе с метаданными (номер, общее количество)
        assertThat(result).isSameAs(page)
        assertThat(result.totalElements).isEqualTo(11)
    }

    @Test
    fun `без фильтров передаёт null, а не значения по умолчанию`() {
        whenever(
            paymentRepository.getList(
                merchantId = merchantId,
                createdFrom = null,
                createdTo = null,
                status = null,
                pageable = pageable,
            )
        ).thenReturn(PageImpl(emptyList(), pageable, 0))

        val result = useCase.execute(GetListPaymentsCommand(merchantId = merchantId, pageable = pageable))

        assertThat(result).isEmpty()
        // null означает "фильтр не задан" — репозиторий тогда не добавляет условие в запрос.
        // Фильтр по продавцу при этом есть всегда
        verify(paymentRepository).getList(
            merchantId = merchantId,
            createdFrom = null,
            createdTo = null,
            status = null,
            pageable = pageable,
        )
    }

}
