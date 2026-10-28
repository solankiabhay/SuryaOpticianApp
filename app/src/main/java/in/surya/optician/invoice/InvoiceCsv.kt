package in.surya.optician.invoice

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import in.surya.optician.data.Invoice
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InvoiceCsv {
 fun export(context: Context, invoices: List<Invoice>): File {
  val dir=File(context.cacheDir,"shared-invoices")
  check(dir.exists() || dir.mkdirs()) { "Unable to create export directory" }
  val file=File(dir,"surya-invoices-${SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(Date())}.csv")
  fun cell(value: String): String = "\"" + value.replace('"', "\"\"").replace("\r"," ").replace("\n"," ") + "\""
  val csv=buildString {
   appendLine("Invoice Number,Created At,Customer,Phone,Subtotal Paise,Discount Paise,Tax Paise,Total Paise,Paid Paise,Due Paise")
   invoices.forEach { invoice ->
    appendLine(listOf(cell(invoice.number),cell(SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(Date(invoice.createdAt))),cell(invoice.customerName),cell(invoice.phone),invoice.subtotalPaise,invoice.discountPaise,invoice.taxPaise,invoice.totalPaise,invoice.paidPaise,invoice.duePaise).joinToString(","))
   }
  }
  FileOutputStream(file).use { it.write(csv.toByteArray(Charsets.UTF_8)) }
  return file
 }
 fun share(context: Context, invoices: List<Invoice>) {
  try {
   val file=export(context,invoices)
   val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
   val intent=Intent(Intent.ACTION_SEND).apply {
    type="text/csv"
    putExtra(Intent.EXTRA_STREAM,uri)
    putExtra(Intent.EXTRA_SUBJECT,"Surya Optician invoice report")
    clipData=ClipData.newUri(context.contentResolver,"Invoice CSV report",uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
   }
   context.startActivity(Intent.createChooser(intent,"Export invoice report"))
  } catch (error: Exception) {
   Toast.makeText(context,error.message ?: "Export failed",Toast.LENGTH_LONG).show()
  }
 }
}
