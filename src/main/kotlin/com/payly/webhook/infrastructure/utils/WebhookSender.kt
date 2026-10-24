package com.payly.webhook.infrastructure.utils

import com.payly.merchant.infrastructure.persistence.repository.MerchantJpaRepository
import com.payly.webhook.infrastructure.persistense.entity.WebhookDeliveryJpaEntity
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant

@Component
class WebhookSender (
    private val merchantRepository: MerchantJpaRepository,
) {

    private val client: RestClient = RestClient.builder()
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(5))
                setReadTimeout(Duration.ofSeconds(10))
            }
        )
        .build()

    fun send (delivery: WebhookDeliveryJpaEntity): WebhookResult {
        val merchant = merchantRepository.findById(delivery.merchantId)
        // Продавца нет (или у него не настроен webhook_url) — слать некуда
        if (merchant.isEmpty) return WebhookResult.Skipped
        val url = merchant.get().webhookUrl
            ?: return WebhookResult.Skipped

        val timestamp = Instant.now().epochSecond
        val signature = WebhookSigner.sign(
            timestamp,
            delivery.payload,
            merchant.get().webhookSecret
        )

        return try {
            val response = client.post()
                .uri(url)
                .header("X-Signature", signature)
                .header("Content-Type", "application/json")
                .body(delivery.payload)
                .retrieve()
                .toBodilessEntity()

            val code = response.statusCode.value() ?: 0
            if (code in 200..299) return WebhookResult.Success(code)
            else return WebhookResult.Failure(code, "non-2xx response")
        } catch (e: Exception) {
            WebhookResult.Failure(0, e.message ?: "unknown error")
        }
    }

}