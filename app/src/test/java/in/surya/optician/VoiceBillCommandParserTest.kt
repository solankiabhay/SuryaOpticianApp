package `in`.surya.optician

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceBillCommandParserTest {
    @Test fun parsesCustomerAndPhone() {
        val command = VoiceBillCommandParser.parse("customer name Rahul phone number 9876543210")
        assertEquals("Rahul", command.customerName)
        assertEquals("9876543210", command.phone)
    }

    @Test fun parsesHinglishItemAndAdvance() {
        val command = VoiceBillCommandParser.parse("add item frame 1500 rupees advance 500 cash")
        assertEquals("frame", command.itemDescription)
        assertEquals("1500", command.itemAmount)
        assertEquals("500", command.advanceAmount)
        assertEquals("Cash", command.paymentMethod)
    }

    @Test fun ignoresInvalidAgeAndEmptyInput() {
        assertEquals(null, VoiceBillCommandParser.parse("age 150").age)
        assertEquals(VoiceBillCommand(), VoiceBillCommandParser.parse(" "))
    }
}
