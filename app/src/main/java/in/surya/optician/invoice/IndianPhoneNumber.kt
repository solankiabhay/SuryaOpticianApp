package com.surya.optician.invoice

/** Normalizes common Indian mobile-number formats to country-code + digits for wa.me links. */
object IndianPhoneNumber {
    fun normalize(raw: String): String {
        var digits = raw.filter(Char::isDigit)
        if (digits.startsWith("00")) digits = digits.drop(2)
        return when {
            digits.length == 10 -> "91$digits"
            digits.length == 12 && digits.startsWith("91") -> digits
            digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
            else -> ""
        }
    }
}
