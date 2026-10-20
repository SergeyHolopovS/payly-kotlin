package com.payly.webhook.infrastructure.utils

sealed interface WebhookResult {
    data class Success(val code: Int) : WebhookResult
    data class Failure(val code: Int, val error: String) : WebhookResult
    object Skipped : WebhookResult
}