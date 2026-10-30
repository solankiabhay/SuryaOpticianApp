package `in`.surya.optician.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrescriptionRulesTest {
    @Test
    fun acceptsValidOpticalPowerAndAxisRanges() {
        assertTrue(PrescriptionRules.isValidPower("-30.00", -30.0, 30.0))
        assertTrue(PrescriptionRules.isValidPower("+5.00", 0.0, 5.0))
        assertTrue(PrescriptionRules.isValidAxis("0"))
        assertTrue(PrescriptionRules.isValidAxis("180"))
    }

    @Test
    fun rejectsInvalidPowerAndAxisValues() {
        assertFalse(PrescriptionRules.isValidPower("31", -30.0, 30.0))
        assertFalse(PrescriptionRules.isValidPower("not-a-number", -30.0, 30.0))
        assertFalse(PrescriptionRules.isValidAxis("181"))
        assertFalse(PrescriptionRules.isValidAxis("90.5"))
    }

    @Test
    fun acceptsMonocularAndBinocularPd() {
        assertTrue(PrescriptionRules.isValidPd("62"))
        assertTrue(PrescriptionRules.isValidPd("31/31"))
        assertFalse(PrescriptionRules.isValidPd("101"))
        assertFalse(PrescriptionRules.isValidPd("31/70"))
    }

    @Test
    fun togglesSignedDecimalValues() {
        assertEquals("-2.00", PrescriptionRules.toggleSign("+2.00"))
        assertEquals("+2.00", PrescriptionRules.toggleSign("-2.00"))
        assertEquals("-2.00", PrescriptionRules.toggleSign("2.00"))
    }
}
