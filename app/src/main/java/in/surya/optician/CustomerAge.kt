package `in`.surya.optician

/** Validates the optional customer age field without coupling the rule to Compose UI. */
data class CustomerAgeValidation(val isValid: Boolean, val age: Int?)

fun validateCustomerAge(raw: String): CustomerAgeValidation {
    val value = raw.trim()
    if (value.isEmpty()) return CustomerAgeValidation(isValid = true, age = null)
    val age = value.toIntOrNull()
        ?: return CustomerAgeValidation(isValid = false, age = null)
    return if (age in 0..120) {
        CustomerAgeValidation(isValid = true, age = age)
    } else {
        CustomerAgeValidation(isValid = false, age = null)
    }
}
