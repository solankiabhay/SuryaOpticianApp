package `in`.surya.optician.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PaymentAccountingTest {
    @Test
    fun editingPaymentReplacesOnlyThatTransaction() {
        assertEquals(75000L, PaymentAccounting.revisedPaidTotal(
            currentPaidPaise = 50000L,
            oldPaymentPaise = 20000L,
            newPaymentPaise = 45000L,
            invoiceTotalPaise = 100000L
        ))
    }

    @Test
    fun editingPaymentCannotExceedInvoiceTotal() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentAccounting.revisedPaidTotal(90000L, 20000L, 30001L, 100000L)
        }
    }

    @Test
    fun deletingPaymentRecalculatesPaidTotal() {
        assertEquals(30000L, PaymentAccounting.paidTotalAfterDelete(50000L, 20000L))
    }

    @Test
    fun deletingPaymentCannotMakePaidTotalNegative() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentAccounting.paidTotalAfterDelete(10000L, 20000L)
        }
    }

    @Test
    fun paymentAmountsMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            PaymentAccounting.revisedPaidTotal(50000L, 20000L, 0L, 100000L)
        }
    }
}
