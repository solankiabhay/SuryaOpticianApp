package `in`.surya.optician

import `in`.surya.optician.data.Customer
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerEditFormTest {
    @Test
    fun editFormLoadsEveryCustomerField() {
        val customer = Customer(
            id = 42L,
            name = "Aarav Sharma",
            phone = "+91 9876543210",
            address = "Pune",
            age = 35,
            email = "aarav@example.com"
        )

        assertEquals(
            CustomerEditForm(
                customerId = 42L,
                name = "Aarav Sharma",
                phone = "+91 9876543210",
                address = "Pune",
                age = "35",
                email = "aarav@example.com"
            ),
            customer.toEditForm()
        )
    }

    @Test
    fun editFormHandlesMissingOptionalFields() {
        val customer = Customer(id = 7L, name = "Test Customer")

        assertEquals("", customer.toEditForm().phone)
        assertEquals("", customer.toEditForm().address)
        assertEquals("", customer.toEditForm().age)
        assertEquals("", customer.toEditForm().email)
    }

    @Test
    fun editFormKeepsTheDatabaseId() {
        val customer = Customer(id = 987L, name = "ID Test")

        assertEquals(987L, customer.toEditForm().customerId)
    }
}
