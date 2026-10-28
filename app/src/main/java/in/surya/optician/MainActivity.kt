package in.surya.optician

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import in.surya.optician.data.*
import in.surya.optician.invoice.InvoicePdf
import in.surya.optician.invoice.InvoiceSharing
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val db = OpticianDatabase.get(this)
  lifecycleScope.launch { if (db.dao().settings() == null) db.dao().saveSettings(ShopSettings()) }
  setContent {
   MaterialTheme {
    var query by remember { mutableStateOf("") }
    val invoices by db.dao().searchInvoices(query).collectAsState(initial = emptyList())
    val customers by db.dao().customers().collectAsState(initial = emptyList())
    val products by db.dao().products().collectAsState(initial = emptyList())
    val due by db.dao().outstanding().collectAsState(initial = 0L)
    Scaffold(topBar = { TopAppBar(title = { Text("Surya Optician") }) }) { padding ->
     Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
      Text("Optical Shop Dashboard", style = MaterialTheme.typography.headlineSmall)
      Text("Customers: ${customers.size}  Products: ${products.size}")
      Text("Outstanding: ₹${"%.2f".format(due / 100.0)}")
      Spacer(Modifier.height(12.dp))
      OutlinedTextField(query, { query = it }, label = { Text("Search invoice, customer, phone") }, modifier = Modifier.fillMaxWidth())
      Spacer(Modifier.height(8.dp))
      LazyColumn {
       items(invoices, key = { it.id }) { invoice ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
         Column(Modifier.padding(12.dp)) {
          Text("${invoice.number} · ${invoice.customerName}", style = MaterialTheme.typography.titleMedium)
          Text("Total ₹${"%.2f".format(invoice.totalPaise / 100.0)} · Due ₹${"%.2f".format(invoice.duePaise / 100.0)}")
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
           TextButton(onClick = { lifecycleScope.launch {
            runCatching {
             val file = InvoicePdf.generate(this@MainActivity, invoice, db.dao().items(invoice.id), db.dao().settings() ?: ShopSettings())
             val uri = FileProvider.getUriForFile(this@MainActivity, "${packageName}.fileprovider", file)
             startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }.onFailure { Toast.makeText(this@MainActivity, "PDF preview failed. Install a PDF viewer.", Toast.LENGTH_LONG).show() }
           } }) { Text("Preview PDF") }
           Button(onClick = { lifecycleScope.launch {
            runCatching {
             val file = InvoicePdf.generate(this@MainActivity, invoice, db.dao().items(invoice.id), db.dao().settings() ?: ShopSettings())
             InvoiceSharing.share(this@MainActivity, file, invoice.phone, true)
            }.onFailure { Toast.makeText(this@MainActivity, "Invoice share failed.", Toast.LENGTH_LONG).show() }
           } }) { Text("Send Bill on WhatsApp") }
          }
          if (invoice.duePaise > 0) {
           TextButton(onClick = {
            val phone = InvoiceSharing.normalizeIndianPhone(invoice.phone)
            if (phone.isBlank()) Toast.makeText(this@MainActivity, "Customer phone is missing or invalid", Toast.LENGTH_LONG).show()
            else {
             lifecycleScope.launch {
              val settings = db.dao().settings() ?: ShopSettings()
              val message = "Payment reminder for invoice ${invoice.number}: outstanding ₹${"%.2f".format(invoice.duePaise / 100.0)}. Please contact ${settings.name} ${settings.phone}."
              val url = "https://wa.me/$phone?text=" + URLEncoder.encode(message, "UTF-8")
              startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
             }
            }
           }) { Text("Payment reminder") }
          }
         }
        }
       }
      }
     }
    }
   }
  }
 }
}
