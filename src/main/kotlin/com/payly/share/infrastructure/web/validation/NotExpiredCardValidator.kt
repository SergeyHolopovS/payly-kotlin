package com.payly.share.infrastructure.web.validation

import com.payly.share.infrastructure.web.dto.request.CardRequest
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.time.YearMonth

class NotExpiredCardValidator : ConstraintValidator<NotExpiredCard, CardRequest> {

    override fun isValid(value: CardRequest?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        if (value.expMonth !in 1..12) return true

        val expiration = YearMonth.of(value.expYear, value.expMonth)
        return !expiration.isBefore(YearMonth.now())
    }
}
