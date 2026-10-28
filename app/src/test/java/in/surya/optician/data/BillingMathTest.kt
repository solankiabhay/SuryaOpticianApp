package com.surya.optician.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BillingMathTest {
    @Test fun lineTotalUsesIntegerPaise() {
        assertEquals(2_750L, BillingMath.lineTotal(quantity = 2, ratePaise = 1_500, discountPaise = 500, taxPaise = 250))
    }

    @Test fun invoiceTotalSubtractsDiscountAndAddsTax() {
        assertEquals(10_500L, BillingMath.invoiceTotal(subtotalPaise = 10_000, discountPaise = 1_000, taxPaise = 1_500))
    }

    @Test fun discountCannotExceedLineSubtotal() {
        assertThrows(IllegalArgumentException::class.java) {
            BillingMath.lineTotal(quantity = 1, ratePaise = 500, discountPaise = 501, taxPaise = 0)
        }
    }

    @Test fun discountCannotExceedInvoiceSubtotal() {
        assertThrows(IllegalArgumentException::class.java) {
            BillingMath.invoiceTotal(subtotalPaise = 100, discountPaise = 101, taxPaise = 0)
        }
    }

    @Test fun multiplicationOverflowIsRejected() {
        assertThrows(ArithmeticException::class.java) {
            BillingMath.lineGross(quantity = Int.MAX_VALUE, ratePaise = Long.MAX_VALUE)
        }
    }

    @Test fun addingAmountsRejectsOverflow() {
        assertThrows(ArithmeticException::class.java) {
            BillingMath.addAmounts(listOf(Long.MAX_VALUE, 1L))
        }
    }
}
