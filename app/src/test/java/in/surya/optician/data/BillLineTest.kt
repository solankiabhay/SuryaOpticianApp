package `in`.surya.optician.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BillLineTest {
    @Test
    fun totalIncludesQuantityDiscountAndTax() {
        val line = BillLine(
            productId = 7L,
            description = "Lens",
            qty = 2,
            ratePaise = 12_500,
            discountPaise = 1_000,
            taxPaise = 500
        )
        assertEquals(24_500L, line.total)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDiscountGreaterThanLineSubtotal() {
        BillLine(
            productId = null,
            description = "Adjustment",
            qty = 1,
            ratePaise = 100,
            discountPaise = 500
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankDescription() {
        BillLine(productId = null, description = "  ", qty = 1, ratePaise = 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroQuantity() {
        BillLine(productId = null, description = "Frame", qty = 0, ratePaise = 100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeRate() {
        BillLine(productId = null, description = "Frame", qty = 1, ratePaise = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeDiscount() {
        BillLine(productId = null, description = "Frame", qty = 1, ratePaise = 100, discountPaise = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeTax() {
        BillLine(productId = null, description = "Frame", qty = 1, ratePaise = 100, taxPaise = -1)
    }
}
