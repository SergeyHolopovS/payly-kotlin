package com.payly.support.data

import com.payly.payment.domain.model.PaymentStatus
import com.payly.support.fixtures.TestCards
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Заводит платежи напрямую через JDBC, минуя API и use case создания.
 *
 * Нужен тестам на чтение: через POST нельзя получить платёж в произвольном статусе
 * (CANCELED, REFUNDED) или с заданной датой создания — а фильтрам списка нужны именно они.
 *
 * Для статуса PROCESSING используйте [createProcessing]: обычный PROCESSING-платёж
 * через пару секунд подхватит PaymentProcessingScheduler и сменит статус прямо во время теста.
 */
class PaymentTestData(private val jdbcTemplate: JdbcTemplate) {

    /**
     * Создаёт платёж и возвращает его id. Все параметры имеют значения по умолчанию —
     * в тесте указывается только то, что для него важно.
     */
    fun create(
        merchantId: String = TestMerchants.DEFAULT_MERCHANT_ID,
        amount: Long = 10_000,
        currency: String = "RUB",
        status: PaymentStatus = PaymentStatus.SUCCEEDED,
        description: String? = "Order #42",
        metadataJson: String = """{"orderId": "42"}""",
        cardLast4: String = "4242",
        failureReason: String? = null,
        refundedAmount: Long = 0,
        // Postgres хранит timestamptz с точностью до микросекунд — обрезаем заранее,
        // чтобы значение из БД совпадало с тем, что тест передал
        createdAt: Instant = Instant.now().truncatedTo(ChronoUnit.MICROS),
    ): String {
        // Тот же формат id, что генерирует PaymentJpaEntity
        val id = "pay_" + UUID.randomUUID().toString().replace("-", "")

        jdbcTemplate.update(
            """
            INSERT INTO payments (
                id, merchant_id, amount, currency, status, description, metadata,
                card_last4, failure_reason, refunded_amount, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
            """.trimIndent(),
            id, UUID.fromString(merchantId), amount, currency, status.name, description, metadataJson,
            cardLast4, failureReason, refundedAmount, Timestamp.from(createdAt), Timestamp.from(createdAt),
        )

        return id
    }

    /**
     * Платёж в PROCESSING, который планировщик не тронет ближайшие 30 секунд
     * (см. [com.payly.support.fixtures.TestCards.SLOW_PROCESSING]).
     */
    fun createProcessing(merchantId: String = TestMerchants.DEFAULT_MERCHANT_ID): String =
        create(
            merchantId = merchantId,
            status = PaymentStatus.PROCESSING,
            cardLast4 = TestCards.SLOW_PROCESSING.takeLast(4),
        )

}
