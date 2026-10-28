package in.surya.optician
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import in.surya.optician.data.*
import in.surya.optician.invoice.*
import kotlinx.coroutines.launch
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);val db=OpticianDatabase.get(this);lifecycleScope.launch{if(db.dao().settings()==null)db.dao().saveSettings(ShopSettings())}
 setContent{MaterialTheme{var q by remember{mutableStateOf("")};val invoices by db.dao().searchInvoices(q).collectAsState(initial=emptyList());val customers by db.dao().customers().collectAsState(initial=emptyList());val products by db.dao().products().collectAsState(initial=emptyList());val due by db.dao().outstanding().collectAsState(initial=0L)
 Scaffold(topBar={TopAppBar(title={Text("Surya Optician")})}){pad->Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)){Text("Optical Shop Dashboard",style=MaterialTheme.typography.headlineSmall);Text("Customers: ${customers.size}  Products: ${products.size}");Text("Outstanding: ₹${"%.2f".format(due/100.0)}");Spacer(Modifier.height(12.dp));OutlinedTextField(q,{q=it},label={Text("Search invoice, customer, phone")},modifier=Modifier.fillMaxWidth());LazyColumn{items(invoices,key={it.id}){i->Card(Modifier.fillMaxWidth().padding(vertical=4.dp)){Column(Modifier.padding(12.dp)){Text("${i.number} · ${i.customerName}",style=MaterialTheme.typography.titleMedium);Text("Total ₹${"%.2f".format(i.totalPaise/100.0)} · Due ₹${"%.2f".format(i.duePaise/100.0)}");Row{TextButton(onClick={lifecycleScope.launch{runCatching{val f=InvoicePdf.generate(this@MainActivity,i,db.dao().items(i.id),db.dao().settings()?:ShopSettings());startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply{setDataAndType(androidx.core.content.FileProvider.getUriForFile(this@MainActivity,"${packageName}.fileprovider",f),"application/pdf");addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)})}.onFailure{android.widget.Toast.makeText(this@MainActivity,"PDF preview failed",android.widget.Toast.LENGTH_LONG).show()}}}){Text("Preview PDF")};Button(onClick={lifecycleScope.launch{runCatching{val f=InvoicePdf.generate(this@MainActivity,i,db.dao().items(i.id),db.dao().settings()?:ShopSettings());InvoiceSharing.share(this@MainActivity,f,i.phone,true)}.onFailure{android.widget.Toast.makeText(this@MainActivity,"Invoice share failed",android.widget.Toast.LENGTH_LONG).show()}}}){Text("Send Bill on WhatsApp")}};if(i.duePaise>0)TextButton(onClick={val n=InvoiceSharing.normalizeIndianPhone(i.phone);if(n.isBlank()){android.widget.Toast.makeText(this@MainActivity,"Customer phone is missing or invalid",android.widget.Toast.LENGTH_LONG).show()}else{val msg=java.net.URLEncoder.encode("Payment reminder for invoice ${i.number}: outstanding ₹${"%.2f".format(i.duePaise/100.0)}. Please contact ${db.dao().settings()?:ShopSettings()}.","UTF-8");startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse("https://wa.me/$n?text=$msg")))}}){Text("Payment reminder")}}}}}}}}}
}
