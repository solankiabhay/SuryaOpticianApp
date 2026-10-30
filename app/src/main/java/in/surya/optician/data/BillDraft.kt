package `in`.surya.optician.data

import java.math.BigDecimal
import java.math.RoundingMode

/** Safe conversion of user-typed rupee amounts into paise (no floating-point rounding errors). */
object Money {
    private val plainAmount = Regex("^(\\d+(\\.\\d*)?|\\.\\d+)$")

    /**
     * Parses text such as "4500", "4,500.50" or "₹99.9" into paise.
     * Returns [blankAs] for blank input and null for invalid or negative input.
     */
    fun parseRupeesToPaise(text: String, blankAs: Long? = null): Long? {
        val cleaned = text.trim().replace("₹", "").replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return blankAs
        if (!plainAmount.matches(cleaned)) return null
        return try {
            BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }
}

/** State of the item currently typed in the "Add items" form. */
sealed class PendingItem {
    /** Nothing meaningful has been typed. */
    object None : PendingItem()

    /** Something was typed but it cannot become a bill line yet. */
    data class Invalid(val message: String) : PendingItem()

    /** A complete, valid item that must be included in the bill. */
    data class Ready(val line: BillLine) : PendingItem()
}

object BillDraft {
    fun parsePending(
        productId: String,
        description: String,
        qty: String,
        rate: String,
        discount: String,
        tax: String
    ): PendingItem {
        val hasDescription = description.isNotBlank()
        val hasRate = rate.isNotBlank()
        if (!hasDescription && !hasRate && productId.isBlank()) return PendingItem.None
        if (!hasDescription) return PendingItem.Invalid("Enter the item description")
        if (!hasRate) return PendingItem.Invalid("Enter the rate for \"${description.trim()}\"")

        val quantity = qty.trim().toIntOrNull()
        if (quantity == null || quantity <= 0) return PendingItem.Invalid("Quantity must be a whole number greater than zero")
        val ratePaise = Money.parseRupeesToPaise(rate) ?: return PendingItem.Invalid("Rate is not a valid amount")
        val discountPaise = Money.parseRupeesToPaise(discount, blankAs = 0L) ?: return PendingItem.Invalid("Discount is not a valid amount")
        val taxPaise = Money.parseRupeesToPaise(tax, blankAs = 0L) ?: return PendingItem.Invalid("Tax is not a valid amount")
        val pid: Long? = if (productId.isBlank()) null else (productId.trim().toLongOrNull() ?: return PendingItem.Invalid("Selected product is invalid"))

        return try {
            PendingItem.Ready(BillLine(pid, description.trim(), quantity, ratePaise, discountPaise, taxPaise))
        } catch (e: IllegalArgumentException) {
            PendingItem.Invalid(e.message ?: "Invalid item")
        } catch (e: ArithmeticException) {
            PendingItem.Invalid("Amount is too large")
        }
    }
}

/** Customer details typed on the bill; resolved to a saved customer inside the billing transaction. */
data class CustomerInput(
    val id: Long?,
    val name: String,
    val phone: String,
    val address: String = "",
    val age: Int? = null,
    val email: String = ""
)

object CustomerMatcher {
    fun digits(phone: String): String = phone.filter(Char::isDigit)

    /** Two names refer to the same person when equal ignoring case/spaces, or one is a whole-word prefix of the other. */
    fun namesCompatible(a: String, b: String): Boolean {
        val x = a.trim().lowercase().replace(Regex("\\s+"), " ")
        val y = b.trim().lowercase().replace(Regex("\\s+"), " ")
        if (x.isEmpty() || y.isEmpty()) return true
        return x == y || x.startsWith("$y ") || y.startsWith("$x ")
    }

    /**
     * Finds the saved customer a bill belongs to, or null when a new customer must be created.
     * - an explicit id always wins;
     * - the same phone number is the same customer only when the names are compatible
     *   (family members may share one phone number);
     * - with no phone, only an existing customer that also has no phone and the same name matches.
     */
    fun findExisting(customers: List<Customer>, id: Long?, phone: String, name: String): Customer? {
        if (id != null) return customers.firstOrNull { it.id == id }
        val d = digits(phone)
        if (d.isNotEmpty()) {
            return customers.firstOrNull { digits(it.phone) == d && namesCompatible(it.name, name) }
        }
        val n = name.trim()
        if (n.isEmpty()) return null
        return customers.filter { digits(it.phone).isEmpty() && it.name.trim().equals(n, ignoreCase = true) }.singleOrNull()
    }
}

object PrescriptionRules {
    private fun number(text: String): Double? = text.trim().removePrefix("+").toDoubleOrNull()

    fun isValidPower(text: String, min: Double, max: Double): Boolean {
        if (text.isBlank()) return true
        val v = number(text) ?: return false
        return v in min..max
    }

    fun isValidAxis(text: String): Boolean {
        if (text.isBlank()) return true
        val v = text.trim().toIntOrNull() ?: return false
        return v in 0..180
    }

    /** Accepts "62" or monocular "31/31". */
    fun isValidPd(text: String): Boolean {
        if (text.isBlank()) return true
        val parts = text.trim().split("/")
        return when (parts.size) {
            1 -> (number(parts[0]) ?: return false) in 20.0..100.0
            2 -> parts.all { (number(it) ?: return false) in 10.0..60.0 }
            else -> false
        }
    }

    fun toggleSign(value: String): String {
        val t = value.trim()
        return when {
            t.startsWith("-") -> "+" + t.drop(1)
            t.startsWith("+") -> "-" + t.drop(1)
            else -> "-$t"
        }
    }

    /** First human-readable problem found in the prescription, or null when everything is acceptable. */
    fun firstError(
        rightSphere: String, rightCylinder: String, rightAxis: String, rightAdd: String,
        leftSphere: String, leftCylinder: String, leftAxis: String, leftAdd: String, pd: String
    ): String? {
        fun power(label: String, v: String, min: Double, max: Double): String? =
            if (isValidPower(v, min, max)) null else "$label must be a number between ${min.toInt()} and +${max.toInt()}"
        return power("Right SPH", rightSphere, -30.0, 30.0)
            ?: power("Right CYL", rightCylinder, -15.0, 15.0)
            ?: (if (isValidAxis(rightAxis)) null else "Right AXIS must be a whole number from 0 to 180")
            ?: power("Right ADD", rightAdd, 0.0, 5.0)
            ?: power("Left SPH", leftSphere, -30.0, 30.0)
            ?: power("Left CYL", leftCylinder, -15.0, 15.0)
            ?: (if (isValidAxis(leftAxis)) null else "Left AXIS must be a whole number from 0 to 180")
            ?: power("Left ADD", leftAdd, 0.0, 5.0)
            ?: (if (isValidPd(pd)) null else "PD must be a number such as 62")
    }
}

/** Builds non-stock charges for an eye test or a prescription-only invoice. */
object PrescriptionBilling {
    fun chargeLine(feeText: String, prescriptionAttached: Boolean): BillLine? {
        if (feeText.isBlank()) {
            return if (prescriptionAttached) BillLine(null, "Eye prescription", 1, 0L) else null
        }
        val amount = Money.parseRupeesToPaise(feeText)
            ?: throw IllegalArgumentException("Eye test charge is not a valid amount")
        require(amount >= 0L) { "Eye test charge cannot be negative" }
        if (amount == 0L && !prescriptionAttached) return null
        return BillLine(null, if (prescriptionAttached) "Eye test / prescription fee" else "Eye test fee", 1, amount)
    }
}

/** Basic field validation shared by the forms. Each function returns an error message or null. */
object Validation {
    fun phoneError(text: String): String? {
        if (text.isBlank()) return null
        val digits = text.filter(Char::isDigit)
        return if (digits.length in 10..12) null else "Enter a valid 10-digit phone number"
    }

    fun ageError(text: String): String? {
        if (text.isBlank()) return null
        val age = text.trim().toIntOrNull()
        return if (age != null && age in 0..120) null else "Age must be between 0 and 120"
    }

    fun amountError(text: String, label: String, allowBlank: Boolean = false, positive: Boolean = false): String? {
        if (text.isBlank()) return if (allowBlank) null else "Enter $label"
        val paise = Money.parseRupeesToPaise(text) ?: return "$label is not a valid amount"
        return if (positive && paise <= 0L) "$label must be more than zero" else null
    }

    fun wholeNumberError(text: String, label: String): String? {
        if (text.isBlank()) return "Enter $label"
        val n = text.trim().toIntOrNull()
        return if (n != null && n >= 0) null else "$label must be a whole number (0 or more)"
    }
}


/** Pure payment-ledger calculations shared by edit/delete operations and unit tests. */
object PaymentAccounting {
    fun revisedPaidTotal(currentPaidPaise: Long, oldPaymentPaise: Long, newPaymentPaise: Long, invoiceTotalPaise: Long): Long {
        require(currentPaidPaise >= 0L && oldPaymentPaise > 0L && newPaymentPaise > 0L && invoiceTotalPaise >= 0L) {
            "Payment amounts must be positive and valid"
        }
        require(oldPaymentPaise <= currentPaidPaise) { "Existing payment is not included in the invoice paid total" }
        val revised = Math.addExact(Math.subtractExact(currentPaidPaise, oldPaymentPaise), newPaymentPaise)
        require(revised <= invoiceTotalPaise) { "Updated payments cannot exceed the invoice total" }
        return revised
    }

    fun paidTotalAfterDelete(currentPaidPaise: Long, paymentPaise: Long): Long {
        require(currentPaidPaise >= 0L && paymentPaise > 0L && paymentPaise <= currentPaidPaise) {
            "Payment cannot be removed from the current paid total"
        }
        return Math.subtractExact(currentPaidPaise, paymentPaise)
    }
}
