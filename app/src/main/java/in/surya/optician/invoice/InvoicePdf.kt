package `in`.surya.optician.invoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import `in`.surya.optician.data.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdf {
    private val ink = android.graphics.Color.rgb(27, 43, 61)
    private val muted = android.graphics.Color.rgb(94, 108, 122)
    private val accent = android.graphics.Color.rgb(16, 113, 107)
    private val pale = android.graphics.Color.rgb(238, 247, 246)
    private val lineColor = android.graphics.Color.rgb(222, 229, 233)

    fun generate(context: Context, invoice: Invoice, items: List<InvoiceItem>, shop: ShopSettings, customer: Customer? = null): File {
        val dir = File(context.filesDir, "invoices")
        check(dir.exists() || dir.mkdirs()) { "Unable to create invoice directory" }
        val file = File(dir, invoice.number.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".pdf")
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val left = 42f
        val right = 553f
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = 0f

        fun paint(color: Int, size: Float = 10f, bold: Boolean = false): Paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
        val regular = paint(ink)
        val secondary = paint(muted, 9f)
        val bold = paint(ink, 10f, true)
        fun text(value: String, x: Float, baseline: Float, p: Paint = regular) {
            canvas.drawText(value.replace(Regex("[\\r\\n\\t]+"), " ").take(90), x, baseline, p)
        }
        fun rule(top: Float, color: Int = lineColor) {
            canvas.drawLine(left, top, right, top, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 0.8f })
        }
        fun startNextPage() {
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = 48f
            text(shop.name.ifBlank { "Surya Optician" }, left, y, paint(ink, 15f, true))
            text("INVOICE  •  ${invoice.number}", left, y + 22f, paint(accent, 10f, true))
            rule(y + 34f)
            y += 56f
        }
        fun ensureSpace(height: Float) {
            if (y + height > 770f) startNextPage()
        }
        try {
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 150f, Paint().apply { color = ink })
            text(shop.name.ifBlank { "Surya Optician" }, left, 49f, paint(android.graphics.Color.WHITE, 23f, true))
            text("OPTICAL CARE • EYEWEAR • LENSES", left, 70f, paint(android.graphics.Color.rgb(195, 221, 222), 8.5f, true))
            var headerY = 94f
            listOf(shop.address, if (shop.phone.isNotBlank()) "Phone: ${shop.phone}" else "", if (shop.gstin.isNotBlank()) "GSTIN: ${shop.gstin}" else "")
                .filter { it.isNotBlank() }.forEach { text(it, left, headerY, paint(android.graphics.Color.WHITE, 9f)); headerY += 14f }
            text("TAX INVOICE", 390f, 45f, paint(android.graphics.Color.WHITE, 17f, true))
            text(invoice.number, 390f, 66f, paint(android.graphics.Color.rgb(195, 221, 222), 10f, true))
            text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(invoice.createdAt)), 390f, 84f, paint(android.graphics.Color.WHITE, 9f))
            text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(invoice.createdAt)), 390f, 99f, paint(android.graphics.Color.WHITE, 9f))

            y = 181f
            text("BILL TO", left, y, paint(accent, 9f, true))
            text(invoice.customerName.ifBlank { "Walk-in customer" }, left, y + 23f, paint(ink, 15f, true))
            if (invoice.phone.isNotBlank()) text("Phone: ${invoice.phone}", left, y + 41f, secondary)
            if (customer?.address?.isNotBlank() == true) text("Address: ${customer.address}", left, y + 56f, secondary)
            if (customer?.age != null) text("Age: ${customer.age}", left, y + 71f, secondary)
            if (invoice.glassType.isNotBlank()) text("Glass type: ${invoice.glassType}", left, y + 86f, secondary)
            if (invoice.deliveryDate.isNotBlank()) text("Expected delivery: ${invoice.deliveryDate}", left, y + 101f, paint(accent, 9f, true))
            text("Invoice status", 400f, y, secondary)
            val status = if (invoice.duePaise <= 0) "PAID" else if (invoice.paidPaise > 0) "PARTIALLY PAID" else "PAYMENT DUE"
            val statusPaint = paint(if (invoice.duePaise <= 0) accent else android.graphics.Color.rgb(166, 91, 22), 9f, true)
            canvas.drawRoundRect(395f, y + 8f, 553f, y + 31f, 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (invoice.duePaise <= 0) pale else android.graphics.Color.rgb(255, 245, 228) })
            text(status, 405f, y + 23f, statusPaint)
            y += if (customer?.address?.isNotBlank() == true || customer?.age != null || invoice.glassType.isNotBlank() || invoice.deliveryDate.isNotBlank()) 129f else 69f
            if (invoice.prescriptionSummary.isNotBlank()) {
                ensureSpace(82f)
                canvas.drawRoundRect(left, y, right, y + 22f, 4f, 4f, Paint().apply { color = pale })
                text("PRESCRIPTION DETAILS", left + 9f, y + 15f, paint(accent, 9f, true))
                y += 34f
                invoice.prescriptionSummary.split("\n").filter { it.isNotBlank() }.forEach { detail ->
                    ensureSpace(16f)
                    text(detail, left + 4f, y, paint(ink, 8.5f))
                    y += 13f
                }
                y += 8f
            }

            canvas.drawRoundRect(left, y, right, y + 29f, 4f, 4f, Paint().apply { color = ink })
            text("ITEM DESCRIPTION", left + 10f, y + 19f, paint(android.graphics.Color.WHITE, 8.5f, true))
            text("QTY", 344f, y + 19f, paint(android.graphics.Color.WHITE, 8.5f, true))
            text("RATE", 390f, y + 19f, paint(android.graphics.Color.WHITE, 8.5f, true))
            text("AMOUNT", 474f, y + 19f, paint(android.graphics.Color.WHITE, 8.5f, true))
            y += 29f
            items.forEachIndexed { index, item ->
                ensureSpace(35f)
                val rowHeight = 34f
                if (index % 2 == 0) canvas.drawRect(left, y, right, y + rowHeight, Paint().apply { color = android.graphics.Color.rgb(247, 249, 250) })
                text(item.description.ifBlank { "Optical product" }, left + 10f, y + 14f, paint(ink, 9.5f, true))
                text("Item ${index + 1}", left + 10f, y + 27f, paint(muted, 7.5f))
                text(item.qty.toString(), 350f, y + 20f, regular)
                text(money(item.ratePaise), 390f, y + 20f, regular)
                text(money(item.totalPaise), 474f, y + 20f, bold)
                y += rowHeight
            }
            y += 18f
            ensureSpace(180f)
            val summaryX = 342f
            fun summary(label: String, amount: String, strong: Boolean = false, height: Float = 22f) {
                text(label, summaryX, y, if (strong) bold else secondary)
                val p = if (strong) paint(ink, 11f, true) else regular
                text(amount, right - p.measureText(amount), y, p)
                y += height
            }
            rule(y - 4f)
            summary("Subtotal", money(invoice.subtotalPaise))
            summary("Discount", "- ${money(invoice.discountPaise)}")
            summary("Tax", money(invoice.taxPaise))
            canvas.drawRoundRect(330f, y - 6f, right, y + 30f, 5f, 5f, Paint().apply { color = pale })
            summary("GRAND TOTAL", money(invoice.totalPaise), true, 36f)
            summary("Amount paid", money(invoice.paidPaise))
            summary("Balance due", money(invoice.duePaise), true)
            y += 24f
            ensureSpace(70f)
            rule(y)
            y += 24f
            text("Thank you for choosing ${shop.name.ifBlank { "Surya Optician" }}!", left, y, paint(accent, 11f, true))
            y += 17f
            text(shop.footer.ifBlank { "We appreciate your trust. Visit us again." }, left, y, secondary)
            y += 24f
            text("Computer-generated invoice • Please retain for your records", left, y, paint(muted, 7.5f))
            canvas.drawLine(left, 808f, right, 808f, Paint().apply { color = lineColor; strokeWidth = 0.8f })
            text("SURYA OPTICIAN  •  THANK YOU", left, 826f, paint(muted, 7.5f, true))
            text("Page $pageNumber", 505f, 826f, paint(muted, 7.5f))
            doc.finishPage(page)
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
        return file
    }

    private fun money(paise: Long): String = "₹%,.2f".format(Locale("en", "IN"), paise / 100.0)
}
