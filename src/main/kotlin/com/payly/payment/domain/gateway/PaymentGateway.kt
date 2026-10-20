package com.payly.payment.domain.gateway

/**
 * Порт для внешнего платёжного гейтвея/эквайера. Принимает полный номер карты
 * (используется только транзитно для похода во внешнюю систему — в БД
 * сохраняются только последние 4 цифры, см. [com.payly.payment.domain.model.Payment.cardLast4]).
 */
interface PaymentGateway {

    fun charge(cardNumber: String): PaymentGatewayResult

}
