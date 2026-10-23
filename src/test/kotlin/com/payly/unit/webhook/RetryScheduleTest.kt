package com.payly.unit.webhook

import com.payly.support.UnitTest
import com.payly.webhook.infrastructure.utils.RetrySchedule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.time.Instant

/**
 * Unit-тесты [RetrySchedule]: экспоненциальная лестница задержек между
 * повторными отправками вебхука с джиттером ±20% и предел числа попыток.
 */
@UnitTest
class RetryScheduleTest {

    @ParameterizedTest(name = "после попытки {0} следующая через ~{1} мин")
    @CsvSource(
        "1, 1",
        "2, 5",
        "3, 30",
        "4, 120",
        "5, 300",
        "6, 600",
        "7, 1440",
    )
    fun `задержка растёт по лестнице и отклоняется не больше чем на 20 процентов`(attempts: Int, minutes: Long) {
        val base = Duration.ofMinutes(minutes)
        val jitter = base.multipliedBy(2).dividedBy(10)

        val before = Instant.now()
        val next = RetrySchedule.nextAttemptAt(attempts)
        val after = Instant.now()

        assertThat(next).isNotNull()
        assertThat(next).isBetween(before + base - jitter, after + base + jitter)
    }

    @Test
    fun `после последней попытки ретраев больше нет`() {
        // MAX_ATTEMPTS = первая отправка + 7 повторов
        assertThat(RetrySchedule.nextAttemptAt(RetrySchedule.MAX_ATTEMPTS)).isNull()
        assertThat(RetrySchedule.nextAttemptAt(RetrySchedule.MAX_ATTEMPTS + 1)).isNull()
    }

    @Test
    fun `предпоследняя попытка ещё получает ретрай`() {
        assertThat(RetrySchedule.nextAttemptAt(RetrySchedule.MAX_ATTEMPTS - 1)).isNotNull()
    }

    @RepeatedTest(20)
    fun `следующая попытка всегда в будущем`() {
        // Джиттер не должен "перебросить" попытку в прошлое
        assertThat(RetrySchedule.nextAttemptAt(1)).isAfter(Instant.now())
    }

    @Test
    fun `джиттер разносит повторы во времени`() {
        // Без джиттера все упавшие вебхуки ретраились бы одновременно
        val samples = (1..20).map { RetrySchedule.nextAttemptAt(3)!!.toEpochMilli() }

        assertThat(samples.toSet()).hasSizeGreaterThan(1)
    }

}
