package `in`.surya.optician

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun parsesCompleteBillAndMultipleItems() {
        val command = VoiceBillCommandParser.parse(
            "customer Rahul Sharma, phone 9876543210, age 30, address Pune, frame 1500 rupees, progressive lens 2500 rupees, lens type progressive, delivery 5 November, discount 200, advance 1000 cash"
        )
        assertEquals("Rahul Sharma", command.customerName)
        assertEquals("Pune", command.address)
        assertEquals(2, command.items.size)
        assertEquals("frame", command.items[0].description)
        assertEquals("progressive lens", command.items[1].description)
        assertEquals("Progressive", command.glassType?.replaceFirstChar { it.uppercase() })
        assertEquals("05/11/2026", command.deliveryDate)
        assertEquals("200", command.discount)
        assertEquals("1000", command.advanceAmount)
    }

    @Test fun parsesPrescriptionFieldsAndSignedValues() {
        val command = VoiceBillCommandParser.parse(
            "right eye SPH minus 2, CYL minus 0.5, axis 180, left eye SPH minus 1.5, CYL minus 0.25, axis 90, PD 62"
        )
        assertEquals("-2", command.rightSphere)
        assertEquals("-0.5", command.rightCylinder)
        assertEquals("-1.5", command.leftSphere)
        assertEquals("-0.25", command.leftCylinder)
        assertEquals("62", command.pd)
    }

    @Test fun ignoresInvalidAgeAndEmptyInput() {
        assertEquals(null, VoiceBillCommandParser.parse("age 150").age)
        assertEquals(VoiceBillCommand(), VoiceBillCommandParser.parse(" "))
        assertTrue(VoiceBillCommandParser.parse("random words").items.isEmpty())
    }
}
