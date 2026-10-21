package com.payly.refund.domain.repository

import com.payly.refund.domain.model.Refund

interface RefundRepository {

    fun save(
        paymentId: String,
        amount: Long,
    ): Refund

    fun update(refund: Refund): Refund

    fun getById(id: String): Refund

    fun getByPaymentId(paymentId: String): List<Refund>

}
