package com.payly.share.web.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class LuhnValidator : ConstraintValidator<Luhn, String> {

    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null) return true
        // Формат (только цифры, длина) проверяет @Pattern — здесь считаем,
        // что на вход может прийти и некорректная строка, поэтому не валим с исключением
        if (value.any { !it.isDigit() }) return true

        var sum = 0
        var doubleDigit = false

        for (index in value.length - 1 downTo 0) {
            var digit = value[index] - '0'
            if (doubleDigit) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            doubleDigit = !doubleDigit
        }

        return sum % 10 == 0
    }
}
