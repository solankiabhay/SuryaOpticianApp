package `in`.surya.optician.invoice

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import `in`.surya.optician.data.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object InvoicePdf {
 fun generate(context: Context, invoice: Invoice, items: List<InvoiceItem>, shop: ShopSettings): File {
  val dir=File(context.filesDir,"invoices")
  check(dir.exists() || dir.mkdirs()) { "Unable to create invoice directory" }
  val file=File(dir,invoice.number.replace(Regex("[^A-Za-z0-9_-]"),"_")+".pdf")
  val doc=PdfDocument()
  try {
   var pageNumber=1
   var page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create())
   var canvas=page.canvas
   val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=android.graphics.Color.BLACK }
   var y=48f
   fun nextPage() {
    doc.finishPage(page)
    pageNumber++
    page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,pageNumber).create())
    canvas=page.canvas
    y=48f
   }
   fun line(text: String, size: Float=12f, bold: Boolean=false) {
    if(y>780f) nextPage()
    paint.textSize=size
    paint.typeface=android.graphics.Typeface.create("sans-serif",if(bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    canvas.drawText(text.take(85),40f,y,paint)
    y+=size+8
   }
   line(shop.name,22f,true)
   if(shop.address.isNotBlank()) line(shop.address)
   if(shop.phone.isNotBlank()) line("Phone: ${shop.phone}")
   if(shop.gstin.isNotBlank()) line("GSTIN: ${shop.gstin}")
   y+=8
   line("INVOICE",17f,true)
   line("Invoice: ${invoice.number}")
   line("Date: "+SimpleDateFormat("dd MMM yyyy, hh:mm a",Locale.getDefault()).format(Date(invoice.createdAt)))
   line("Customer: ${invoice.customerName}")
   if(invoice.phone.isNotBlank()) line("Phone: ${invoice.phone}")
   y+=8
   line("Description | Qty | Rate | Total",10f,true)
   items.forEach { line("${it.description} | ${it.qty} | ${money(it.ratePaise)} | ${money(it.totalPaise)}",10f) }
   y+=10
   line("Subtotal: ${money(invoice.subtotalPaise)}")
   line("Discount: ${money(invoice.discountPaise)}")
   line("Tax: ${money(invoice.taxPaise)}")
   line("Grand total: ${money(invoice.totalPaise)}",15f,true)
   line("Paid: ${money(invoice.paidPaise)}")
   line("Outstanding: ${money(invoice.duePaise)}",13f,true)
   if(shop.footer.isNotBlank()) line(shop.footer)
   doc.finishPage(page)
   FileOutputStream(file).use { doc.writeTo(it) }
  } finally { doc.close() }
  return file
 }
 private fun money(p: Long)="INR %.2f".format(Locale.US,p/100.0)
}
