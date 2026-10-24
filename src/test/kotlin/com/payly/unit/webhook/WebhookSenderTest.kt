package com.payly.unit.webhook

import com.payly.merchant.infrastructure.persistence.entity.MerchantJpaEntity
import com.payly.merchant.infrastructure.persistence.repository.MerchantJpaRepository
import com.payly.support.UnitTest
import com.payly.webhook.infrastructure.persistense.entity.WebhookDeliveryJpaEntity
import com.payly.webhook.infrastructure.utils.WebhookResult
import com.payly.webhook.infrastructure.utils.WebhookSender
import com.payly.webhook.infrastructure.utils.WebhookSigner
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever
import java.net.InetSocketAddress
import java.util.Optional
import java.util.UUID

/**
 * Unit-тесты [WebhookSender]: продавец берётся из мока репозитория, а вебхук
 * уходит на настоящий HTTP-сервер из JDK на свободном порту — так проверяется
 * реальный запрос (тело, подпись), а не вызовы RestClient.
 */
@UnitTest
class WebhookSenderTest {

    @Mock
    private lateinit var merchantRepository: MerchantJpaRepository

    private lateinit var sender: WebhookSender

    private lateinit var server: HttpServer

    // Что сервер ответит и что он получил
    private var responseCode = 200
    private var receivedBody: String? = null
    private var receivedSignature: String? = null

    private val merchantId = UUID.randomUUID()
    private val secret = "whsec_test"
    private val delivery = WebhookDeliveryJpaEntity(
        merchantId = merchantId,
        eventType = "PAYMENT_SUCCEEDED",
        payload = """{"type":"PAYMENT_SUCCEEDED"}""",
    )

    @BeforeEach
    fun setUp() {
        sender = WebhookSender(merchantRepository)

        server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
            createContext("/webhook") { exchange ->
                receivedBody = exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
                receivedSignature = exchange.requestHeaders.getFirst("X-Signature")
                exchange.sendResponseHeaders(responseCode, -1)
                exchange.close()
            }
            start()
        }
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun `найденному продавцу вебхук доставляется с телом и подписью`() {
        givenMerchant(webhookUrl = webhookUrl())

        val result = sender.send(delivery)

        assertThat(result).isEqualTo(WebhookResult.Success(200))
        assertThat(receivedBody).isEqualTo(delivery.payload)

        // Подпись считается от того же timestamp, что пришёл в заголовке, — продавец проверит её так же
        val timestamp = receivedSignature!!.substringAfter("t=").substringBefore(",").toLong()
        assertThat(receivedSignature).isEqualTo(WebhookSigner.sign(timestamp, delivery.payload, secret))
    }

    @Test
    fun `ответ продавца не 2xx — неудачная попытка, а не успех`() {
        givenMerchant(webhookUrl = webhookUrl())
        responseCode = 500

        val result = sender.send(delivery)

        assertThat(result).isInstanceOf(WebhookResult.Failure::class.java)
    }

    @Test
    fun `продавца нет — доставка пропускается`() {
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.empty())

        assertThat(sender.send(delivery)).isEqualTo(WebhookResult.Skipped)
    }

    @Test
    fun `у продавца не настроен webhook_url — доставка пропускается`() {
        givenMerchant(webhookUrl = null)

        assertThat(sender.send(delivery)).isEqualTo(WebhookResult.Skipped)
        assertThat(receivedBody).isNull()
    }

    private fun webhookUrl() = "http://localhost:${server.address.port}/webhook"

    private fun givenMerchant(webhookUrl: String?) {
        val merchant = MerchantJpaEntity(
            id = merchantId,
            name = "Test merchant",
            apiKeyHash = "hash",
            webhookUrl = webhookUrl,
            webhookSecret = secret,
        )
        whenever(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant))
    }

}
