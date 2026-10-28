package in.surya.optician.invoice

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNormalizationTest {
    @Test fun tenDigitIndianNumberAddsCountryCode() =
        assertEquals("919876543210", IndianPhoneNumber.normalize("9876543210"))

    @Test fun plus91IsNotDuplicated() =
        assertEquals("919876543210", IndianPhoneNumber.normalize("+91 98765 43210"))

    @Test fun leadingZeroIsNormalized() =
        assertEquals("919876543210", IndianPhoneNumber.normalize("09876543210"))

    @Test fun internationalDialPrefixIsNormalized() =
        assertEquals("919876543210", IndianPhoneNumber.normalize("0091 98765 43210"))

    @Test fun invalidNumberIsRejected() =
        assertEquals("", IndianPhoneNumber.normalize("123"))

    @Test fun nonIndianCountryCodeIsRejected() =
        assertEquals("", IndianPhoneNumber.normalize("+1 202 555 0123"))
}
