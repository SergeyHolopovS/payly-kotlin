package com.payly.unit.webhook

import com.payly.support.UnitTest
import com.payly.webhook.infrastructure.utils.WebhookSigner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Unit-тесты [WebhookSigner]: формат заголовка X-Signature и то, что продавец
 * может проверить подпись стандартным HMAC-SHA256 по "timestamp.body".
 */
@UnitTest
class WebhookSignerTest {

    private val body = """{"id":"evt_1","type":"PAYMENT_SUCCEEDED"}"""

    @Test
    fun `подпись имеет формат t=timestamp,v1=hex`() {
        val signature = WebhookSigner.sign(1_700_000_000, body, "whsec_test")

        assertThat(signature).matches("t=1700000000,v1=[0-9a-f]{64}")
    }

    @Test
    fun `v1 — это HMAC-SHA256 от timestamp и тела запроса через точку`() {
        val signature = WebhookSigner.sign(1_700_000_000, body, "whsec_test")

        // Так подпись проверяет продавец на своей стороне
        val expected = hmacSha256Hex("whsec_test", "1700000000.$body")
        assertThat(signature).isEqualTo("t=1700000000,v1=$expected")
    }

    @Test
    fun `подпись детерминирована`() {
        assertThat(WebhookSigner.sign(42, body, "whsec_test"))
            .isEqualTo(WebhookSigner.sign(42, body, "whsec_test"))
    }

    @Test
    fun `подпись зависит от секрета, тела и времени`() {
        val original = WebhookSigner.sign(42, body, "whsec_test")

        assertThat(WebhookSigner.sign(42, body, "whsec_other")).isNotEqualTo(original)
        assertThat(WebhookSigner.sign(42, "$body ", "whsec_test")).isNotEqualTo(original)
        // Время входит в подписываемую строку — подменить t в заголовке нельзя
        assertThat(WebhookSigner.sign(43, body, "whsec_test").substringAfter("v1="))
            .isNotEqualTo(original.substringAfter("v1="))
    }

    @Test
    fun `тело подписывается в UTF-8`() {
        val unicodeBody = """{"description":"Заказ №42"}"""

        assertThat(WebhookSigner.sign(1, unicodeBody, "секрет"))
            .isEqualTo("t=1,v1=" + hmacSha256Hex("секрет", "1.$unicodeBody"))
    }

    private fun hmacSha256Hex(secret: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

}
