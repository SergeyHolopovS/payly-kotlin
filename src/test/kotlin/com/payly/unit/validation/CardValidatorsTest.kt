package com.payly.unit.validation

import com.payly.share.infrastructure.web.dto.request.CardRequest
import com.payly.share.infrastructure.web.validation.LuhnValidator
import com.payly.share.infrastructure.web.validation.NotExpiredCardValidator
import com.payly.share.infrastructure.web.validation.SupportedCurrencyValidator
import com.payly.support.UnitTest
import jakarta.validation.ConstraintValidatorContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import java.time.YearMonth

/**
 * Unit-тесты кастомных валидаторов запроса: алгоритм Луна, срок действия карты
 * и список поддерживаемых валют. Контекст валидации им не нужен — передаётся мок.
 */
@UnitTest
class CardValidatorsTest {

    @Mock
    private lateinit var context: ConstraintValidatorContext

    @Nested
    inner class Luhn {

        private val validator = LuhnValidator()

        @ParameterizedTest
        @ValueSource(strings = ["4242424242424242", "4000000000000002", "5555555555554444", "79927398713"])
        fun `номер с верной контрольной суммой проходит`(number: String) {
            assertThat(validator.isValid(number, context)).isTrue()
        }

        @ParameterizedTest
        @ValueSource(strings = ["4242424242424241", "4000000000000001", "79927398710"])
        fun `номер с неверной контрольной суммой отклоняется`(number: String) {
            assertThat(validator.isValid(number, context)).isFalse()
        }

        @Test
        fun `null пропускается — обязательность проверяет NotBlank`() {
            assertThat(validator.isValid(null, context)).isTrue()
        }

        @Test
        fun `строка не из цифр пропускается без исключения — формат проверяет Pattern`() {
            assertThat(validator.isValid("4242-4242-4242-4242", context)).isTrue()
        }
    }

    @Nested
    inner class NotExpiredCard {

        private val validator = NotExpiredCardValidator()

        @Test
        fun `карта, истекающая в текущем месяце, ещё действительна`() {
            val now = YearMonth.now()

            assertThat(validator.isValid(aCard(now.monthValue, now.year), context)).isTrue()
        }

        @Test
        fun `карта с будущим сроком действительна`() {
            val next = YearMonth.now().plusYears(2)

            assertThat(validator.isValid(aCard(next.monthValue, next.year), context)).isTrue()
        }

        @Test
        fun `карта, истёкшая в прошлом месяце, отклоняется`() {
            val previous = YearMonth.now().minusMonths(1)

            assertThat(validator.isValid(aCard(previous.monthValue, previous.year), context)).isFalse()
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 13, -1])
        fun `некорректный месяц пропускается без исключения — его проверяют Min и Max`(month: Int) {
            assertThat(validator.isValid(aCard(month, 2020), context)).isTrue()
        }

        @Test
        fun `null пропускается`() {
            assertThat(validator.isValid(null, context)).isTrue()
        }

        private fun aCard(expMonth: Int, expYear: Int) =
            CardRequest(number = "4242424242424242", expMonth = expMonth, expYear = expYear, cvc = "123")
    }

    @Nested
    inner class SupportedCurrency {

        private val validator = SupportedCurrencyValidator(listOf("RUB", "USD"))

        @Test
        fun `валюта из списка проходит`() {
            assertThat(validator.isValid("RUB", context)).isTrue()
        }

        @ParameterizedTest
        @ValueSource(strings = ["EUR", "rub", ""])
        fun `валюта не из списка отклоняется, регистр важен`(currency: String) {
            assertThat(validator.isValid(currency, context)).isFalse()
        }

        @Test
        fun `null пропускается`() {
            assertThat(validator.isValid(null, context)).isTrue()
        }
    }

}
