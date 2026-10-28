package `in`.surya.optician.invoice
import org.junit.Assert.assertEquals
import org.junit.Test
class PhoneNormalizationTest {
 @Test fun tenDigitIndianNumberAddsCountryCode() = assertEquals("919876543210", InvoiceSharing.normalizeIndianPhone("9876543210"))
 @Test fun plus91IsNotDuplicated() = assertEquals("919876543210", InvoiceSharing.normalizeIndianPhone("+91 98765 43210"))
 @Test fun leadingZeroIsNormalized() = assertEquals("919876543210", InvoiceSharing.normalizeIndianPhone("09876543210"))
 @Test fun invalidNumberIsRejected() = assertEquals("", InvoiceSharing.normalizeIndianPhone("123"))
}
