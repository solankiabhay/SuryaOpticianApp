package com.surya.optician.invoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.surya.optician.data.Invoice
import com.surya.optician.data.InvoiceItem
import com.surya.optician.data.ShopSettings
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoicePdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 40f
    private const val TOP = 48f
    private const val BOTTOM = 790f

    fun generate(context: Context, invoice: Invoice, items: List<InvoiceItem>, shop: ShopSettings): File {
        val directory = File(context.filesDir, "invoices")
        check(directory.exists() || directory.mkdirs()) { "Could not create invoice directory" }
        val safeNumber = invoice.number.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(directory, "$safeNumber.pdf")
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y = TOP

        fun nextPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = TOP
        }

        fun line(text: String, size: Float = 12f, bold: Boolean = false) {
            if (y + size + 8f > BOTTOM) nextPage()
            paint.textSize = size
            paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(text.take(85), LEFT, y, paint)
            y += size + 8f
        }

        try {
            line(shop.name, 22f, true)
            if (shop.address.isNotBlank()) line(shop.address)
            if (shop.phone.isNotBlank()) line("Phone: ${shop.phone}")
            if (shop.gstin.isNotBlank()) line("GSTIN: ${shop.gstin}")
            y += 8f
            line("INVOICE", 17f, true)
            line("Invoice: ${invoice.number}")
            line("Date: " + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(invoice.createdAt)))
            line("Customer: ${invoice.customerName}")
            if (invoice.phone.isNotBlank()) line("Phone: ${invoice.phone}")
            y += 8f
            line("Description | Qty | Rate | Total", 10f, true)
            items.forEach { item ->
                line("${item.description} | ${item.qty} | ${money(item.ratePaise)} | ${money(item.totalPaise)}", 10f)
            }
            y += 10f
            line("Subtotal: ${money(invoice.subtotalPaise)}")
            line("Discount: ${money(invoice.discountPaise)}")
            line("Tax: ${money(invoice.taxPaise)}")
            line("Grand total: ${money(invoice.totalPaise)}", 15f, true)
            line("Paid: ${money(invoice.paidPaise)}")
            line("Outstanding: ${money(invoice.duePaise)}", 13f, true)
            if (shop.footer.isNotBlank()) line(shop.footer)
            document.finishPage(page)
            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return file
    }

    private fun money(paise: Long): String = "INR %.2f".format(Locale.US, paise / 100.0)
}
