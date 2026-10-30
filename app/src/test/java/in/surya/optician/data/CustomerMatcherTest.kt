package `in`.surya.optician.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerMatcherTest {
    private val customers = listOf(
        Customer(id = 1, name = "Aarav Sharma", phone = "+91 98765 43210"),
        Customer(id = 2, name = "Riya Patel", phone = "")
    )

    @Test
    fun explicitCustomerIdTakesPriority() {
        assertEquals(2L, CustomerMatcher.findExisting(customers, 2L, "99999", "Different Name")?.id)
    }

    @Test
    fun normalizedPhoneMatchesCompatibleCustomerName() {
        assertEquals(1L, CustomerMatcher.findExisting(customers, null, "+91-9876543210", "Aarav")?.id)
    }

    @Test
    fun sharedPhoneDoesNotMergeIncompatibleNames() {
        assertNull(CustomerMatcher.findExisting(customers, null, "9876543210", "Other Family"))
    }

    @Test
    fun noPhoneRequiresExactUniqueName() {
        assertEquals(2L, CustomerMatcher.findExisting(customers, null, "", "Riya Patel")?.id)
        assertNull(CustomerMatcher.findExisting(customers, null, "", "Riya"))
    }

    @Test
    fun nameCompatibilityIgnoresCaseAndAllowsWholeWordPrefix() {
        assertTrue(CustomerMatcher.namesCompatible("AARAV SHARMA", "Aarav"))
        assertFalse(CustomerMatcher.namesCompatible("Aarav Sharma", "Aarav Kumar"))
    }
}
