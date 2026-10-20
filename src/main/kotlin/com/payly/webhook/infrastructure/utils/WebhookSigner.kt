package com.payly.webhook.infrastructure.utils

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object WebhookSigner {
    private const val ALGO = "HmacSHA256"

    fun sign(timestamp: Long, rawBody: String, secret: String): String {
        val signedPayload = "$timestamp.$rawBody"
        val mac = Mac.getInstance(ALGO)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGO))
        val hex = mac.doFinal(signedPayload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "t=$timestamp,v1=$hex"
    }
}