package com.payly.unit.webhook

import com.payly.merchant.infrastructure.persistence.entity.MerchantJpaEntity
import com.payly.merchant.infrastructure.persistence.repository.MerchantJpaRepository
import com.payly.support.UnitTest
import com.payly.support.data.TestMerchants
import com.payly.support.webhook.WebhookReceiver
import com.payly.webhook.infrastructure.persistense.entity.WebhookDeliveryJpaEntity
import com.payly.webhook.infrastructure.utils.WebhookResult
import com.payly.webhook.infrastructure.utils.WebhookSender
import com.payly.webhook.infrastructure.utils.WebhookSigner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import java.net.ServerSocket
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Unit-тесты [WebhookSender]: куда и с какой подписью уходит вебхук и как
 * ответ продавца превращается в [WebhookResult]. Репозиторий продавцов — мок,
 * а эндпоинт продавца — настоящий HTTP-сервер на localhost ([WebhookReceiver]),
 * потому что RestClient создаётся внутри WebhookSender.
 */
@UnitTest
class WebhookSenderTest {

    @Mock
    private lateinit var merchantRepository: MerchantJpaRepository

    private lateinit var sender: WebhookSender

    private val receiver = WebhookReceiver()

    private val merchantId = UUID.fromString(TestMerchants.DEFAULT_MERCHANT_ID)

    private val payload = """{"id":"evt_1","type":"PAYMENT_SUCCEEDED","data":{"object":{"paymentId":"pay_1"}}}"""

    @BeforeEach
    fun setUp() {
        sender = WebhookSender(merchantRepository)
    }

    @AfterEach
    fun stopReceiver() = receiver.close()

    // ---------- Успешная отправка ----------

    @Test
    fun `отправляет payload POST-запросом на webhook_url продавца`() {
        givenMerchant(webhookUrl = receiver.url)

        val result = sender.send(aDelivery())

        assertThat(result).isEqualTo(WebhookResult.Success(200))
        val request = receiver.requests.single()
        // Тело уходит байт-в-байт: продавец проверяет подпись именно по нему
        assertThat(request.body).isEqualTo(payload)
        assertThat(request.contentType).startsWith("application/json")
    }

    @Test
    fun `подписывает запрос секретом продавца`() {
        givenMerchant(webhookUrl = receiver.url, webhookSecret = "whsec_merchant")

        val before = Instant.now().epochSecond
        sender.send(aDelivery())
        val after = Instant.now().epochSecond

        val signature = receiver.requests.single().signature!!
        val timestamp = signature.substringAfter("t=").substringBefore(",").toLong()
        assertThat(timestamp).isBetween(before, after)
        // Продавец пересчитывает подпись по timestamp из заголовка и сырому телу
        assertThat(signature).isEqualTo(WebhookSigner.sign(timestamp, payload, "whsec_merchant"))
    }

    @Test
    fun `любой 2xx считается успехом`() {
        givenMerchant(webhookUrl = receiver.url)
        receiver.responseStatus = 204

        assertThat(sender.send(aDelivery())).isEqualTo(WebhookResult.Success(204))
    }

    // ---------- Неуспешная отправка ----------

    @Test
    fun `ответ 5xx — неуспех с кодом ответа`() {
        givenMerchant(webhookUrl = receiver.url)
        receiver.responseStatus = 503

        val result = sender.send(aDelivery())

        // Код нужен в last_response_code, чтобы продавец видел, что отвечал его сервер
        assertThat(result).isInstanceOf(WebhookResult.Failure::class.java)
        assertThat((result as WebhookResult.Failure).code).isEqualTo(503)
    }

    @Test
    fun `ответ 4xx — неуспех с кодом ответа`() {
        givenMerchant(webhookUrl = receiver.url)
        receiver.responseStatus = 400

        val result = sender.send(aDelivery())

        assertThat(result).isInstanceOf(WebhookResult.Failure::class.java)
        assertThat((result as WebhookResult.Failure).code).isEqualTo(400)
    }

    @Test
    fun `недоступный эндпоинт — неуспех с кодом 0 и текстом ошибки`() {
        givenMerchant(webhookUrl = "http://127.0.0.1:${freePort()}/webhooks")

        val result = sender.send(aDelivery())

        assertThat(result).isInstanceOf(WebhookResult.Failure::class.java)
        result as WebhookResult.Failure
        assertThat(result.code).isZero()
        assertThat(result.error).isNotBlank()
    }

    // ---------- Пропуск ----------

    @Test
    fun `продавец без webhook_url пропускается без HTTP-запроса`() {
        givenMerchant(webhookUrl = null)

        assertThat(sender.send(aDelivery())).isEqualTo(WebhookResult.Skipped)
        assertThat(receiver.requests).isEmpty()
    }

    @Test
    fun `несуществующий продавец пропускается`() {
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.empty())

        assertThat(sender.send(aDelivery())).isEqualTo(WebhookResult.Skipped)
        assertThat(receiver.requests).isEmpty()
    }

    // ---------- Хелперы ----------

    private fun givenMerchant(webhookUrl: String?, webhookSecret: String = "whsec_test") {
        whenever(merchantRepository.findById(merchantId)).thenReturn(
            Optional.of(
                MerchantJpaEntity(
                    id = merchantId,
                    name = "Test merchant",
                    apiKeyHash = "test_api_key",
                    webhookUrl = webhookUrl,
                    webhookSecret = webhookSecret,
                )
            )
        )
    }

    private fun aDelivery() = WebhookDeliveryJpaEntity(
        merchantId = merchantId,
        eventType = "PAYMENT_SUCCEEDED",
        payload = payload,
    )

    /** Порт, на котором точно никто не слушает: занимаем свободный и сразу освобождаем. */
    private fun freePort(): Int = ServerSocket(0).use { it.localPort }

}
