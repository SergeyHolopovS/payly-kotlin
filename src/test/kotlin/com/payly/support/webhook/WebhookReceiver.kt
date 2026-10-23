package com.payly.support.webhook

import com.sun.net.httpserver.HttpServer
import org.awaitility.Awaitility.await
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Поддельный эндпоинт продавца для приёма вебхуков: настоящий HTTP-сервер
 * на свободном порту localhost (JDK HttpServer, без лишних зависимостей).
 * Запоминает все пришедшие запросы и отвечает статусом [responseStatus].
 * Использовать в `use { }`.
 */
class WebhookReceiver : AutoCloseable {

    data class ReceivedWebhook(val signature: String?, val contentType: String?, val body: String)

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    private val received = CopyOnWriteArrayList<ReceivedWebhook>()

    /** Статус, которым отвечает эндпоинт; тесты на ретраи меняют его на 5xx. */
    @Volatile
    var responseStatus: Int = 200

    init {
        server.createContext(PATH) { exchange ->
            exchange.use {
                received += ReceivedWebhook(
                    signature = it.requestHeaders.getFirst("X-Signature"),
                    contentType = it.requestHeaders.getFirst("Content-Type"),
                    body = it.requestBody.readAllBytes().decodeToString(),
                )
                // -1 — ответ без тела
                it.sendResponseHeaders(responseStatus, -1)
            }
        }
        server.start()
    }

    /** URL, который прописывается продавцу в webhook_url. */
    val url: String
        get() = "http://127.0.0.1:${server.address.port}$PATH"

    val requests: List<ReceivedWebhook>
        get() = received.toList()

    /**
     * Ждёт, пока придёт хотя бы [minCount] вебхуков под [predicate], и возвращает все такие.
     */
    fun awaitRequests(
        timeout: Duration = Duration.ofSeconds(30),
        minCount: Int = 1,
        predicate: (ReceivedWebhook) -> Boolean = { true },
    ): List<ReceivedWebhook> {
        await().atMost(timeout).until { received.count(predicate) >= minCount }
        return received.filter(predicate)
    }

    override fun close() = server.stop(0)

    private companion object {
        const val PATH = "/webhooks"
    }

}
