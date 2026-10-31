package `in`.surya.optician

import java.util.Calendar

/**
 * Maps the free Android speech-to-text transcript to the existing bill draft.
 * Parsing is deliberately local: no bill is saved by this class.
 */
data class VoiceBillItem(val description: String, val quantity: Int, val amount: String, val discount: String? = null, val tax: String? = null)

data class VoiceBillCommand(
    val customerName: String? = null,
    val phone: String? = null,
    val age: String? = null,
    val address: String? = null,
    val email: String? = null,
    val items: List<VoiceBillItem> = emptyList(),
    val glassType: String? = null,
    val deliveryDate: String? = null,
    val eyeTestFee: String? = null,
    val discount: String? = null,
    val tax: String? = null,
    val advanceAmount: String? = null,
    val paymentMethod: String? = null,
    val rightSphere: String? = null,
    val rightCylinder: String? = null,
    val rightAxis: String? = null,
    val rightAdd: String? = null,
    val leftSphere: String? = null,
    val leftCylinder: String? = null,
    val leftAxis: String? = null,
    val leftAdd: String? = null,
    val pd: String? = null
) {
    // Kept as compatibility accessors for callers/tests written for the first voice release.
    val itemDescription: String? get() = items.firstOrNull()?.description
    val itemAmount: String? get() = items.firstOrNull()?.amount
}

object VoiceBillCommandParser {
    private val number = "(?:minus|negative|plus)?\\s*\\d+(?:\\.\\d{1,2})?"
    private fun capture(text: String, pattern: String): String? =
        Regex(pattern, RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.trim()
            ?.trim(',', '.', ';')?.takeIf { it.isNotBlank() }

    private fun signed(value: String?): String? {
        if (value == null) return null
        val cleaned = value.trim().lowercase()
        val sign = when {
            cleaned.startsWith("minus") || cleaned.startsWith("negative") -> "-"
            cleaned.startsWith("plus") -> "+"
            else -> ""
        }
        val digits = Regex("\\d+(?:\\.\\d{1,2})?").find(cleaned)?.value ?: return null
        return sign + digits
    }

    private fun date(value: String?): String? {
        if (value == null) return null
        val raw = value.trim().trim(',', '.')
        val numeric = Regex("^(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?$").matchEntire(raw)
        if (numeric != null) {
            val day = numeric.groupValues[1].toIntOrNull() ?: return null
            val month = numeric.groupValues[2].toIntOrNull() ?: return null
            val year = numeric.groupValues[3].ifBlank { Calendar.getInstance().get(Calendar.YEAR).toString() }.toIntOrNull() ?: return null
            if (day !in 1..31 || month !in 1..12) return null
            return "%02d/%02d/%04d".format(day, month, if (year < 100) 2000 + year else year)
        }
        val named = Regex("(?i)^(\\d{1,2})\\s+(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)(?:\\s+(\\d{4}))?$").matchEntire(raw) ?: return null
        val months = listOf("jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec")
        val monthText = named.groupValues[2].lowercase().take(3)
        val month = months.indexOf(monthText) + 1
        val day = named.groupValues[1].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        val year = named.groupValues[3].ifBlank { Calendar.getInstance().get(Calendar.YEAR).toString() }
        return "%02d/%02d/%s".format(day, month, year)
    }

    private fun parseItems(text: String, discount: String?, tax: String?): List<VoiceBillItem> {
        val result = mutableListOf<VoiceBillItem>()
        // Spoken item groups are separated by commas/semicolons. Only segments naming an
        // item/frame/lens are accepted, so age, dates and prescription numbers aren't bills.
        text.split(',', ';', '\n').forEach { raw ->
            val original = raw.trim().trimEnd('.')
            val segment = Regex("(?i)^(.*?\\d+(?:\\.\\d{1,2})?\\s*(?:rs\\.?|rupees?)?)(?=\\s+(?:advance|paid|cash|online|upi|other|discount|tax|glass|delivery|right|left|pd)\\b)").find(original)?.groupValues?.get(1) ?: original
            val match = Regex(
                "(?i)^(?:(?:add\\s+item|item|product)\\s+)?(.+?)\\s+(?:rs\\.?|rupees?\\s*)?(\\d+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|rupees?)?$"
            ).matchEntire(segment) ?: return@forEach
            var description = match.groupValues[1].trim()
            if (!Regex("(?i)\\b(frame|lens|item|product)\\b").containsMatchIn(description)) return@forEach
            var qty = 1
            val qtyMatch = Regex("(?i)\\bqty\\s*(\\d+)\\b").find(description)
            if (qtyMatch != null) {
                qty = qtyMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 999) ?: 1
                description = description.replace(qtyMatch.value, "").trim()
            }
            if (description.isNotBlank()) result += VoiceBillItem(description, qty, match.groupValues[2], discount, tax)
        }
        return result
    }

    fun parse(spokenText: String): VoiceBillCommand {
        val text = spokenText.trim()
        if (text.isBlank()) return VoiceBillCommand()
        val stop = "(?=\\s+(?:phone|mobile|number|age|umar|address|email|item|product|frame|lens|glass|lens\\s+type|delivery|date|eye\\s+test|prescription\\s+charge|discount|tax|advance|paid|payment|right|left|od|os|pd)\\b|$)"
        val name = capture(text, "(?:customer(?:\\s+name)?|naam)\\s+(.+?)$stop")
        val phone = capture(text, "(?:phone|mobile|number)\\s*(?:number)?\\s*([0-9][0-9\\s-]{8,14})")
            ?.filter(Char::isDigit)?.takeIf { it.length in 10..12 }
        val age = capture(text, "(?:age|umar)\\s*(\\d{1,3})")?.toIntOrNull()?.takeIf { it in 0..120 }?.toString()
        val address = capture(text, "address\\s+(.+?)$stop")
        val email = capture(text, "email\\s+([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})")
        val discount = capture(text, "(?:discount|chhoot)\\s*(?:rs\\.?|rupees?\\s*)?([0-9]+(?:\\.[0-9]{1,2})?)")
        val tax = capture(text, "tax\\s*(?:rs\\.?|rupees?\\s*)?([0-9]+(?:\\.[0-9]{1,2})?)")
        val glass = capture(text, "(?:glass\\s+type|lens\\s+type)\\s+(.+?)$stop")
        val delivery = date(capture(text, "(?:delivery(?:\\s+date)?|deliver)\\s+(?:on\\s+)?(.+?)$stop"))
        val eyeFee = capture(text, "(?:eye\\s+test\\s+fee|prescription\\s+charge|eye\\s+test)\\s*(?:rs\\.?|rupees?\\s*)?([0-9]+(?:\\.[0-9]{1,2})?)")
        val advance = capture(text, "(?:advance|paid)\\s*(?:rs\\.?|rupees?\\s*)?([0-9]+(?:\\.[0-9]{1,2})?)")
        val method = when {
            Regex("(?i)\\bcash\\b").containsMatchIn(text) -> "Cash"
            Regex("(?i)\\b(online|upi|card)\\b").containsMatchIn(text) -> "Online"
            Regex("(?i)\\b(other)\\b").containsMatchIn(text) -> "Other"
            else -> null
        }
        val rightText = Regex("(?i)(?:right(?:\\s+eye)?|od)\\s*(.*?)(?=(?:left(?:\\s+eye)?|os)\\b|$)").find(text)?.groupValues?.getOrNull(1).orEmpty()
        val leftText = Regex("(?i)(?:left(?:\\s+eye)?|os)\\s*(.*)$").find(text)?.groupValues?.getOrNull(1).orEmpty()
        fun eye(segment: String, field: String): String? {
            val aliases = when (field) {
                "sphere" -> "(?:sph|sphere)"
                "cylinder" -> "(?:cyl|cylinder)"
                "axis" -> "axis"
                else -> "add"
            }
            val value = capture(segment, "$aliases\\s*($number)")
            return if (field == "axis") value?.filter(Char::isDigit)?.let { digits -> digits.toIntOrNull()?.takeIf { it in 0..180 }?.toString() }
            else signed(value)
        }
        val pdValue = capture(text, "(?:pd|p\\.?d\\.?|pupillary\\s+distance)\\s*(\\d{1,3}(?:\\.\\d+)?)")
            ?.toDoubleOrNull()?.takeIf { it in 20.0..80.0 }?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }
        return VoiceBillCommand(
            customerName = name,
            phone = phone,
            age = age,
            address = address,
            email = email,
            items = parseItems(text, discount, tax),
            glassType = glass,
            deliveryDate = delivery,
            eyeTestFee = eyeFee,
            discount = discount,
            tax = tax,
            advanceAmount = advance,
            paymentMethod = method,
            rightSphere = eye(rightText, "sphere"),
            rightCylinder = eye(rightText, "cylinder"),
            rightAxis = eye(rightText, "axis"),
            rightAdd = eye(rightText, "add"),
            leftSphere = eye(leftText, "sphere"),
            leftCylinder = eye(leftText, "cylinder"),
            leftAxis = eye(leftText, "axis"),
            leftAdd = eye(leftText, "add"),
            pd = pdValue
        )
    }
}
