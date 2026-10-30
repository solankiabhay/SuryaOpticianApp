package `in`.surya.optician

import `in`.surya.optician.data.Customer

/**
 * Immutable form snapshot used when opening an existing customer for editing.
 * Keeping the mapping in a small pure function makes the edit-prefill behaviour testable.
 */
data class CustomerEditForm(
    val customerId: Long,
    val name: String,
    val phone: String,
    val address: String,
    val age: String,
    val email: String
)

fun Customer.toEditForm(): CustomerEditForm = CustomerEditForm(
    customerId = id,
    name = name,
    phone = phone,
    address = address,
    age = age?.toString().orEmpty(),
    email = email
)
