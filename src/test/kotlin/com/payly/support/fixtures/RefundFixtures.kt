package com.payly.support.fixtures

import com.payly.refund.domain.model.Refund
import com.payly.refund.domain.model.RefundStatus
import com.payly.refund.infrastructure.web.dto.request.CreateRefundRequest
import java.time.Instant

// Фабрики в том же стиле, что и в PaymentFixtures: валидный объект по умолчанию,
// в тесте переопределяется только то, что проверяется

/** Запрос на возврат; amount = null — вернуть весь оставшийся остаток платежа. */
fun aCreateRefundRequest(amount: Long? = null) = CreateRefundRequest(amount = amount)

/**
 * Доменная модель возврата для unit-тестов. По умолчанию — в PENDING,
 * как её возвращает RefundRepository.save сразу после создания.
 */
fun aRefund(
    id: String = "re_test",
    paymentId: String = "pay_test",
    amount: Long = 10_000,
    status: RefundStatus = RefundStatus.PENDING,
) = Refund(
    id = id,
    paymentId = paymentId,
    amount = amount,
    status = status,
    createdAt = Instant.now(),
)
