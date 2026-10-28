package `in`.surya.optician

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerAgeTest {
    @Test fun blankAgeIsOptional() {
        val result = validateCustomerAge("  ")
        assertTrue(result.isValid)
        assertEquals(null, result.age)
    }

    @Test fun acceptsBoundaryAges() {
        assertEquals(0, validateCustomerAge("0").age)
        assertEquals(120, validateCustomerAge("120").age)
        assertTrue(validateCustomerAge("0").isValid)
        assertTrue(validateCustomerAge("120").isValid)
    }

    @Test fun acceptsTrimmedAge() {
        assertEquals(42, validateCustomerAge(" 42 ").age)
    }

    @Test fun rejectsAgeAbove120() {
        assertFalse(validateCustomerAge("121").isValid)
    }

    @Test fun rejectsNegativeAge() {
        assertFalse(validateCustomerAge("-1").isValid)
    }

    @Test fun rejectsNonNumericAge() {
        assertFalse(validateCustomerAge("abc").isValid)
    }

    @Test fun rejectsIntegerOverflow() {
        assertFalse(validateCustomerAge("999999999999").isValid)
    }
}
