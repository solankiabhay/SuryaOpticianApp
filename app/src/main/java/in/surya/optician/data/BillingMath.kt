package com.surya.optician.data

/** Pure monetary calculations. All amounts are integer paise, never floating point. */
object BillingMath {
    fun lineGross(quantity: Int, ratePaise: Long): Long {
        require(quantity > 0) { "Quantity must be positive" }
        require(ratePaise >= 0) { "Rate cannot be negative" }
        return Math.multiplyExact(quantity.toLong(), ratePaise)
    }

    fun lineTotal(quantity: Int, ratePaise: Long, discountPaise: Long, taxPaise: Long): Long {
        require(discountPaise >= 0 && taxPaise >= 0) { "Discount and tax cannot be negative" }
        val gross = lineGross(quantity, ratePaise)
        require(discountPaise <= gross) { "Line discount cannot exceed line subtotal" }
        return Math.addExact(Math.subtractExact(gross, discountPaise), taxPaise)
    }

    fun invoiceTotal(subtotalPaise: Long, discountPaise: Long, taxPaise: Long): Long {
        require(subtotalPaise >= 0 && discountPaise >= 0 && taxPaise >= 0) {
            "Invoice amounts cannot be negative"
        }
        require(discountPaise <= subtotalPaise) { "Discount cannot exceed subtotal" }
        return Math.addExact(Math.subtractExact(subtotalPaise, discountPaise), taxPaise)
    }

    fun addAmounts(amounts: Iterable<Long>): Long =
        amounts.fold(0L) { total, amount ->
            require(amount >= 0) { "Amounts cannot be negative" }
            Math.addExact(total, amount)
        }
}
