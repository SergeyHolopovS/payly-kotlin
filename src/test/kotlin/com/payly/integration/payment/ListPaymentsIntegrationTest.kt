package com.payly.integration.payment

import com.payly.payment.domain.model.PaymentStatus
import com.payly.support.AbstractIntegrationTest
import com.payly.support.data.TestMerchants
import com.payly.support.security.WithMockMerchant
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

/**
 * Интеграционные тесты `GET /api/v1/payment`: фильтры по статусу и дате
 * создания, пагинация и изоляция продавцов. Здесь проверяется реальная
 * фильтрация — JPA Specifications выполняются на настоящем Postgres.
 *
 * Платежи заводятся напрямую в БД с фиксированными датами, чтобы границы
 * фильтров по дате проверялись точно, а не "примерно сейчас".
 */
@WithMockMerchant
class ListPaymentsIntegrationTest : AbstractIntegrationTest() {

    // Опорные даты для фильтров: по одному платежу в начале, середине и конце января
    private val jan01 = Instant.parse("2026-01-01T00:00:00Z")
    private val jan15 = Instant.parse("2026-01-15T00:00:00Z")
    private val jan31 = Instant.parse("2026-01-31T00:00:00Z")

    // ---------- Без фильтров ----------

    @Test
    fun `возвращает все платежи продавца`() {
        val first = payments.create()
        val second = payments.create()

        listPayments()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(2)))
            // Порядок без sort не гарантирован — сравниваем как множество
            .andExpect(jsonPath("$[*].id", containsInAnyOrder(first, second)))
    }

    @Test
    fun `не возвращает платежи других продавцов`() {
        merchants.create(id = TestMerchants.OTHER_MERCHANT_ID)
        val own = payments.create()
        payments.create(merchantId = TestMerchants.OTHER_MERCHANT_ID)

        // Фильтр по продавцу добавляется всегда, даже когда остальные фильтры не заданы
        listPayments()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].id").value(own))
    }

    @Test
    fun `без платежей возвращает пустой список, а не 404`() {
        listPayments()
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(0)))
    }

    // ---------- Фильтр по статусу ----------

    @Test
    fun `фильтрует по статусу`() {
        val succeeded = payments.create(status = PaymentStatus.SUCCEEDED)
        payments.create(status = PaymentStatus.FAILED)
        payments.create(status = PaymentStatus.CANCELED)

        listPayments("status" to PaymentStatus.SUCCEEDED.name)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].id").value(succeeded))
    }

    @Test
    fun `неизвестный статус отклоняется`() {
        // Строка не конвертируется в enum PaymentStatus → MethodArgumentTypeMismatchException → 400
        listPayments("status" to "UNKNOWN")
            .andExpect(status().isBadRequest)
    }

    // ---------- Фильтр по дате создания ----------

    @Test
    fun `фильтрует по диапазону дат, включая границы`() {
        val atStart = payments.create(createdAt = jan01)
        val inside = payments.create(createdAt = jan15)
        val atEnd = payments.create(createdAt = jan31)
        payments.create(createdAt = jan31.plusSeconds(1))

        // createdBetween использует SQL BETWEEN — обе границы входят в диапазон
        listPayments("createdFrom" to jan01.toString(), "createdTo" to jan31.toString())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id", containsInAnyOrder(atStart, inside, atEnd)))
    }

    @Test
    fun `фильтрует только по нижней границе даты`() {
        payments.create(createdAt = jan01)
        val inside = payments.create(createdAt = jan15)
        val atEnd = payments.create(createdAt = jan31)

        listPayments("createdFrom" to jan15.toString())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id", containsInAnyOrder(inside, atEnd)))
    }

    @Test
    fun `фильтрует только по верхней границе даты`() {
        val atStart = payments.create(createdAt = jan01)
        val inside = payments.create(createdAt = jan15)
        payments.create(createdAt = jan31)

        listPayments("createdTo" to jan15.toString())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id", containsInAnyOrder(atStart, inside)))
    }

    @Test
    fun `некорректная дата отклоняется`() {
        listPayments("createdFrom" to "вчера")
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `фильтры по статусу и дате комбинируются через И`() {
        val match = payments.create(status = PaymentStatus.SUCCEEDED, createdAt = jan15)
        payments.create(status = PaymentStatus.FAILED, createdAt = jan15)    // не тот статус
        payments.create(status = PaymentStatus.SUCCEEDED, createdAt = jan01) // не та дата

        listPayments("status" to PaymentStatus.SUCCEEDED.name, "createdFrom" to jan15.toString())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
            .andExpect(jsonPath("$[0].id").value(match))
    }

    // ---------- Пагинация и сортировка ----------

    @Test
    fun `сортирует и отдаёт страницу по параметрам page, size и sort`() {
        val oldest = payments.create(createdAt = jan01)
        val middle = payments.create(createdAt = jan15)
        val newest = payments.create(createdAt = jan31)

        // Pageable собирается Spring Data из query-параметров; страницы нумеруются с 0
        listPayments("sort" to "createdAt,desc", "size" to "2", "page" to "0")
            .andExpect(status().isOk)
            // contains (в отличие от containsInAnyOrder) проверяет и порядок элементов
            .andExpect(jsonPath("$[*].id", contains(newest, middle)))

        listPayments("sort" to "createdAt,desc", "size" to "2", "page" to "1")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[*].id", contains(oldest)))
    }

    // ---------- Хелперы ----------

    /** GET списка платежей с произвольными query-параметрами. */
    private fun listPayments(vararg params: Pair<String, String>): ResultActions =
        mockMvc.perform(
            get("/api/v1/payment").apply {
                params.forEach { (name, value) -> param(name, value) }
            }
        )

}
