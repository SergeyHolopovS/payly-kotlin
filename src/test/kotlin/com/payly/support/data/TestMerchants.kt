package com.payly.support.data

import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * Фиксированные id тестовых продавцов. Константы (а не UUID.randomUUID()), потому что
 * их нужно передавать в аннотацию [com.payly.support.security.WithMockMerchant].
 */
object TestMerchants {
    const val DEFAULT_MERCHANT_ID = "00000000-0000-0000-0000-000000000001"
    const val OTHER_MERCHANT_ID = "00000000-0000-0000-0000-000000000002"
}

/**
 * Заводит продавцов напрямую через JDBC: у MerchantJpaEntity генерируемый id,
 * а тестам нужен заранее известный (под него настроен [com.payly.support.security.WithMockMerchant]).
 */
class MerchantTestData(private val jdbcTemplate: JdbcTemplate) {

    /**
     * Создаёт продавца и возвращает его id. Все параметры имеют значения по умолчанию —
     * в тесте указывается только то, что для него важно (например, apiKey в e2e).
     */
    fun create(
        id: String = TestMerchants.DEFAULT_MERCHANT_ID,
        name: String = "Test merchant",
        apiKey: String = "test_api_key_$id",
        webhookUrl: String? = null,
    ): UUID {
        val merchantId = UUID.fromString(id)

        // ApiKeyFilter сравнивает Bearer-токен с api_key_hash как есть
        jdbcTemplate.update(
            """
            INSERT INTO merchants (id, name, api_key_hash, webhook_url, webhook_secret)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
            // webhook_url по умолчанию null — WebhookSender пропускает таких продавцов,
            // и тесты не пытаются слать вебхуки наружу
            merchantId, name, apiKey, webhookUrl, "whsec_test",
        )

        return merchantId
    }

}
