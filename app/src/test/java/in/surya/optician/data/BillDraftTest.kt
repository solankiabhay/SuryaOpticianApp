package `in`.surya.optician.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillDraftTest {
    @Test fun moneyParsesWithoutFloatingPointErrors() {
        assertEquals(1999L, Money.parseRupeesToPaise("19.99"))
        assertEquals(29L, Money.parseRupeesToPaise("0.29"))
        assertEquals(450029L, Money.parseRupeesToPaise("4500.29"))
        assertEquals(125050L, Money.parseRupeesToPaise("1,250.50"))
        assertEquals(10000L, Money.parseRupeesToPaise("₹100"))
        assertEquals(1000L, Money.parseRupeesToPaise("10."))
    }

    @Test fun moneyRejectsBadInput() {
        assertNull(Money.parseRupeesToPaise("1.2.3"))
        assertNull(Money.parseRupeesToPaise("-5"))
        assertNull(Money.parseRupeesToPaise("abc"))
        assertNull(Money.parseRupeesToPaise(""))
        assertEquals(0L, Money.parseRupeesToPaise("  ", blankAs = 0L))
    }

    @Test fun emptyItemFormIsNone() {
        assertTrue(BillDraft.parsePending("", "", "1", "", "0", "0") is PendingItem.None)
    }

    @Test fun completeItemIsReadyWithoutPressingAdd() {
        val result = BillDraft.parsePending("", "Ray-Ban frame", "2", "4500", "100", "50")
        assertTrue(result is PendingItem.Ready)
        val line = (result as PendingItem.Ready).line
        assertEquals("Ray-Ban frame", line.description)
        assertEquals(2, line.qty)
        assertEquals(450000L, line.ratePaise)
        assertEquals(10000L, line.discountPaise)
        assertEquals(5000L, line.taxPaise)
    }

    @Test fun partiallyFilledItemIsReportedNotIgnored() {
        assertTrue(BillDraft.parsePending("", "Frame", "1", "", "0", "0") is PendingItem.Invalid)
        assertTrue(BillDraft.parsePending("", "", "1", "500", "0", "0") is PendingItem.Invalid)
        assertTrue(BillDraft.parsePending("", "Frame", "0", "500", "0", "0") is PendingItem.Invalid)
        assertTrue(BillDraft.parsePending("", "Frame", "1", "5x0", "0", "0") is PendingItem.Invalid)
        assertTrue(BillDraft.parsePending("", "Frame", "1", "500", "9999", "0") is PendingItem.Invalid)
    }

    @Test fun blankDiscountAndTaxDefaultToZero() {
        val result = BillDraft.parsePending("", "Lens", "1", "1200", "", "")
        assertTrue(result is PendingItem.Ready)
        assertEquals(0L, (result as PendingItem.Ready).line.discountPaise)
    }

    private val rahul = Customer(id = 1, name = "Rahul Sharma", phone = "98765 43210")
    private val noPhone = Customer(id = 2, name = "Walk In", phone = "")

    @Test fun customerMatchesBySamePhoneAndCompatibleName() {
        assertEquals(1L, CustomerMatcher.findExisting(listOf(rahul), null, "9876543210", "Rahul")?.id)
        assertEquals(1L, CustomerMatcher.findExisting(listOf(rahul), null, "+91 98765 43210".takeLast(11), "rahul sharma")?.id)
    }

    @Test fun sharedPhoneWithDifferentNameCreatesNewCustomer() {
        assertNull(CustomerMatcher.findExisting(listOf(rahul), null, "9876543210", "Priya"))
    }

    @Test fun explicitIdAlwaysWins() {
        assertEquals(1L, CustomerMatcher.findExisting(listOf(rahul, noPhone), 1L, "", "Anything")?.id)
        assertNull(CustomerMatcher.findExisting(listOf(rahul), 99L, "", "Rahul"))
    }

    @Test fun nameOnlyMatchRequiresCustomerWithoutPhone() {
        assertNull(CustomerMatcher.findExisting(listOf(rahul), null, "", "Rahul Sharma"))
        assertEquals(2L, CustomerMatcher.findExisting(listOf(rahul, noPhone), null, "", "walk in")?.id)
    }

    @Test fun formValidation() {
        assertNull(Validation.phoneError(""))
        assertNull(Validation.phoneError("98765 43210"))
        assertNotNull(Validation.phoneError("12345"))
        assertNull(Validation.ageError("45"))
        assertNotNull(Validation.ageError("200"))
        assertNotNull(Validation.amountError("0", "Selling price", positive = true))
        assertNull(Validation.amountError("", "Purchase cost", allowBlank = true))
        assertNotNull(Validation.wholeNumberError("-1", "Stock"))
        assertNull(Validation.wholeNumberError("12", "Stock"))
    }

    @Test fun prescriptionRulesAcceptRealValuesAndRejectNonsense() {
        assertTrue(PrescriptionRules.isValidPower("-2.00", -30.0, 30.0))
        assertTrue(PrescriptionRules.isValidPower("+1.50", 0.0, 5.0))
        assertTrue(PrescriptionRules.isValidPower("", -30.0, 30.0))
        assertFalse(PrescriptionRules.isValidPower("-45", -30.0, 30.0))
        assertFalse(PrescriptionRules.isValidPower("abc", -30.0, 30.0))
        assertTrue(PrescriptionRules.isValidAxis("180"))
        assertFalse(PrescriptionRules.isValidAxis("181"))
        assertTrue(PrescriptionRules.isValidPd("62"))
        assertTrue(PrescriptionRules.isValidPd("31/31"))
        assertFalse(PrescriptionRules.isValidPd("5"))
        assertEquals("+1.50", PrescriptionRules.toggleSign("-1.50"))
        assertEquals("-2.00", PrescriptionRules.toggleSign("2.00"))
        assertNull(PrescriptionRules.firstError("-2.00", "-0.50", "180", "+1.00", "-2.25", "-0.75", "175", "+1.00", "62"))
        assertNotNull(PrescriptionRules.firstError("-2.00", "-0.50", "400", "", "", "", "", "", ""))
    }
}
