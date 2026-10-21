package com.payly.support.fixtures

import com.payly.payment.domain.model.Payment
import com.payly.payment.domain.model.PaymentStatus
import com.payly.payment.infrastructure.web.dto.request.CreatePaymentRequest
import com.payly.share.infrastructure.web.dto.request.CardRequest
import com.payly.support.data.TestMerchants
import java.time.Instant
import java.time.Year
import java.util.UUID

/**
 * Тестовые карты, на которые реагирует
 * [com.payly.payment.infrastructure.gateway.TestModePaymentGateway] (все проходят проверку Луна).
 */
object TestCards {
    const val SUCCESS = "4242424242424242"
    const val DECLINED = "4000000000000002"
    const val INSUFFICIENT_FUNDS = "4000000000009995"
    /**
     * Карта "долгой обработки" (…3220): гейтвей отвечает через 30 с, и столько же
     * PaymentProcessingScheduler ждёт, прежде чем финализировать такой PROCESSING-платёж.
     * Поэтому платёж в PROCESSING для тестов заводится с этими last4 — планировщик
     * не сменит его статус посреди теста.
     */
    const val SLOW_PROCESSING = "4000000000003220"
    /** Проходит формат (16 цифр), но не проверку Луна — для тестов валидации. */
    const val INVALID_LUHN = "4242424242424241"
}

// Фабрики тестовых данных в стиле "a<Объект>(...)": по умолчанию возвращают
// валидный объект, а в тесте переопределяется только поле, которое проверяется.
// Так из теста сразу видно, чем именно он отличается от "нормального" случая

/** Валидная карта: успешный номер и срок действия на 2 года вперёд (чтобы фикстура не "протухла"). */
fun aCardRequest(
    number: String = TestCards.SUCCESS,
    expMonth: Int = 12,
    expYear: Int = Year.now().value + 2,
    cvc: String = "123",
) = CardRequest(
    number = number,
    expMonth = expMonth,
    expYear = expYear,
    cvc = cvc,
)

/** Валидный запрос на создание платежа в поддерживаемой валюте. */
fun aCreatePaymentRequest(
    amount: Long = 10_000,
    currency: String = "RUB",
    description: String? = "Order #42",
    card: CardRequest = aCardRequest(),
    metadata: Map<String, String> = mapOf("orderId" to "42"),
) = CreatePaymentRequest(
    amount = amount,
    currency = currency,
    description = description,
    card = card,
    metadata = metadata,
)

/** Доменная модель платежа для unit-тестов, где нет БД и id/даты проставлять некому. */
fun aPayment(
    id: String = "pay_test",
    merchantId: UUID = UUID.fromString(TestMerchants.DEFAULT_MERCHANT_ID),
    amount: Long = 10_000,
    currency: String = "RUB",
    status: PaymentStatus = PaymentStatus.CREATED,
    description: String? = "Order #42",
    metadata: Map<String, Any?> = mapOf("orderId" to "42"),
    cardLast4: String = TestCards.SUCCESS.takeLast(4),
    failureReason: String? = null,
) = Payment(
    id = id,
    merchantId = merchantId,
    amount = amount,
    currency = currency,
    status = status,
    description = description,
    metadata = metadata,
    cardLast4 = cardLast4,
    failureReason = failureReason,
    refundedAmount = 0,
    version = 0,
    createdAt = Instant.now(),
    updatedAt = Instant.now(),
)
