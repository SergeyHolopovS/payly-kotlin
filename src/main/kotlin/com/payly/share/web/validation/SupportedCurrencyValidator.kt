package com.payly.share.web.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import org.springframework.beans.factory.annotation.Value

class SupportedCurrencyValidator(
    @Value("\${payment.supported-currencies}") private val supportedCurrencies: List<String>,
) : ConstraintValidator<SupportedCurrency, String> {

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true

        return value in supportedCurrencies
    }
}
