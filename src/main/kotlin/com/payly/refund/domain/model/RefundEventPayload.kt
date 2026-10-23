package com.payly.refund.domain.model

import java.util.UUID

/**
 * Payload события REFUND_SUCCEEDED. У возврата нет своего merchantId, поэтому его
 * передаёт вызывающий (берётся из платежа) — по нему консьюмер вебхуков находит продавца.
 */
fun Refund.toEventPayload(merchantId: UUID): Map<String, String> = mapOf(
    "refundId" to id,
    "merchantId" to merchantId.toString(),
    "paymentId" to paymentId,
    "amount" to amount.toString(),
    "status" to status.name,
)
