package `in`.surya.optician

/**
 * Offline command parsing for the free Android speech-to-text result.
 * Speech recognition itself is provided by the device's installed recognizer.
 */
data class VoiceBillCommand(
    val customerName: String? = null,
    val phone: String? = null,
    val age: String? = null,
    val itemDescription: String? = null,
    val itemAmount: String? = null,
    val advanceAmount: String? = null,
    val paymentMethod: String? = null
)

object VoiceBillCommandParser {
    fun parse(spokenText: String): VoiceBillCommand {
        val text = spokenText.trim()
        if (text.isBlank()) return VoiceBillCommand()

        fun capture(pattern: Regex): String? =
            pattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }

        val name = capture(Regex("(?i)(?:customer(?:\\s+name)?|naam)\\s+(.+?)(?=\\s+(?:phone|mobile|number|age|umar|item|advance|payment)\\b|$)"))
        val phone = capture(Regex("(?i)(?:phone|mobile|number)\\s*(?:number)?\\s*([0-9][0-9\\s-]{8,14})"))
            ?.filter(Char::isDigit)?.takeIf { it.length in 10..12 }
        val age = capture(Regex("(?i)(?:age|umar)\\s*(\\d{1,3})"))?.takeIf { it.toIntOrNull() in 0..120 }
        val itemMatch = Regex("(?i)(?:add\\s+item|item|product)\\s+(.+?)\\s+(?:rupees?\\s*)?(\\d+(?:\\.\\d{1,2})?)\\s*(?:rupees?|rs)?\\b").find(text)
        val advance = capture(Regex("(?i)(?:advance|paid)\\s*(?:rupees?\\s*)?(\\d+(?:\\.\\d{1,2})?)"))
        val method = when {
            Regex("(?i)\\b(cash)\\b").containsMatchIn(text) -> "Cash"
            Regex("(?i)\\b(online|upi|card)\\b").containsMatchIn(text) -> "Online"
            Regex("(?i)\\b(other)\\b").containsMatchIn(text) -> "Other"
            else -> null
        }
        return VoiceBillCommand(
            customerName = name?.trim(' ', ',', '.'),
            phone = phone,
            age = age,
            itemDescription = itemMatch?.groupValues?.get(1)?.trim(' ', ',', '.'),
            itemAmount = itemMatch?.groupValues?.get(2),
            advanceAmount = advance,
            paymentMethod = method
        )
    }
}
