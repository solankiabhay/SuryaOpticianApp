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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = OpticianDatabase.get(this)
        val billing = BillingRepository(db)
        lifecycleScope.launch {
            if (db.dao().settings() == null) db.dao().saveSettings(ShopSettings())
        }
        setContent {
            MaterialTheme {
                var query by remember { mutableStateOf("") }
                var dialog by remember { mutableStateOf("") }
                var selectedInvoice by remember { mutableStateOf<Invoice?>(null) }
                var message by remember { mutableStateOf<String?>(null) }
                val invoices by db.dao().searchInvoices(query).collectAsState(initial = emptyList())
                val customers by db.dao().customers().collectAsState(initial = emptyList())
                val products by db.dao().products().collectAsState(initial = emptyList())
                val due by db.dao().outstanding().collectAsState(initial = 0L)
                val lowStock by db.dao().lowStock().collectAsState(initial = emptyList())

                Scaffold(topBar = { TopAppBar(title = { Text("Surya Optician") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                        Text("Optical Shop Dashboard", style = MaterialTheme.typography.headlineSmall)
                        Text("Customers: ${customers.size}  Products: ${products.size}  Invoices: ${invoices.size}")
                        Text("Outstanding: ₹${money(due)}")
                        if (lowStock.isNotEmpty()) {
                            Text("Low stock: ${lowStock.joinToString { "${it.name} (${it.stock})" }}",
                                color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { dialog = "customer" }) { Text("Add customer") }
                            Button(onClick = { dialog = "product" }) { Text("Add product") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Button(onClick = { dialog = "bill" }, modifier = Modifier.fillMaxWidth()) {
                            Text("Create invoice")
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = query, onValueChange = { query = it },
                            label = { Text("Search invoice, customer, phone") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(invoices, key = { it.id }) { invoice ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("${invoice.number} · ${invoice.customerName}",
                                            style = MaterialTheme.typography.titleMedium)
                                        Text("Total ₹${money(invoice.totalPaise)} · Paid ₹${money(invoice.paidPaise)} · Due ₹${money(invoice.duePaise)}")
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TextButton(onClick = {
                                                lifecycleScope.launch {
                                                    runCatching {
                                                        val file = InvoicePdf.generate(
                                                            this@MainActivity, invoice,
                                                            db.dao().items(invoice.id),
                                                            db.dao().settings() ?: ShopSettings()
                                                        )
                                                        val uri = FileProvider.getUriForFile(
                                                            this@MainActivity, "${packageName}.fileprovider", file
                                                        )
                                                        startActivity(
                                                            Intent(Intent.ACTION_VIEW)
                                                                .setDataAndType(uri, "application/pdf")
                                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        )
                                                    }.onFailure { showToast("PDF preview failed. Install a PDF viewer.") }
                                                }
                                            }) { Text("Preview PDF") }
                                            TextButton(onClick = {
                                                lifecycleScope.launch {
                                                    runCatching {
                                                        val file = InvoicePdf.generate(
                                                            this@MainActivity, invoice,
                                                            db.dao().items(invoice.id),
                                                            db.dao().settings() ?: ShopSettings()
                                                        )
                                                        InvoiceSharing.share(this@MainActivity, file, invoice.phone, true)
                                                    }.onFailure { showToast("Invoice share failed.") }
                                                }
                                            }) { Text("WhatsApp PDF") }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (invoice.duePaise > 0) {
                                                TextButton(onClick = {
                                                    selectedInvoice = invoice
                                                    dialog = "reminder"
                                                }) { Text("Payment reminder") }
                                                TextButton(onClick = {
                                                    selectedInvoice = invoice
                                                    dialog = "payment"
                                                }) { Text("Record payment") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                when (dialog) {
                    "customer" -> CustomerDialog(
                        onDismiss = { dialog = "" },
                        onSave = { name, phone, address ->
                            lifecycleScope.launch {
                                runCatching {
                                    require(name.isNotBlank()) { "Customer name is required" }
                                    db.dao().addCustomer(Customer(name = name.trim(), phone = phone.trim(), address = address.trim()))
                                    dialog = ""
                                    showToast("Customer saved")
                                }.onFailure { showToast(it.message ?: "Could not save customer") }
                            }
                        }
                    )
                    "product" -> ProductDialog(
                        onDismiss = { dialog = "" },
                        onSave = { name, sku, price, stock, lowStockThreshold ->
                            lifecycleScope.launch {
                                runCatching {
                                    require(name.isNotBlank()) { "Product name is required" }
                                    require(price >= 0 && stock >= 0 && lowStockThreshold >= 0) { "Price and stock cannot be negative" }
                                    db.dao().addProduct(Product(
                                        name = name.trim(), sku = sku.trim(),
                                        pricePaise = toPaise(price), stock = stock,
                                        lowStock = lowStockThreshold
                                    ))
                                    dialog = ""
                                    showToast("Product saved")
                                }.onFailure { showToast(it.message ?: "Could not save product") }
                            }
                        }
                    )
                    "bill" -> BillDialog(
                        onDismiss = { dialog = "" },
                        onSave = { customerName, phone, description, qty, rate, discount, tax, paid ->
                            lifecycleScope.launch {
                                runCatching {
                                    require(customerName.isNotBlank()) { "Customer name is required" }
                                    require(description.isNotBlank()) { "Item description is required" }
                                    require(qty > 0) { "Quantity must be greater than zero" }
                                    require(rate >= 0 && discount >= 0 && tax >= 0 && paid >= 0) { "Amounts cannot be negative" }
                                    billing.createBill(NewBill(
                                        customerId = null,
                                        customerName = customerName.trim(),
                                        phone = phone.trim(),
                                        lines = listOf(BillLine(
                                            productId = null, description = description.trim(),
                                            qty = qty, ratePaise = toPaise(rate),
                                            discountPaise = toPaise(discount), taxPaise = toPaise(tax)
                                        )),
                                        paidPaise = toPaise(paid)
                                    ))
                                    dialog = ""
                                    showToast("Invoice created")
                                }.onFailure { showToast(it.message ?: "Could not create invoice") }
                            }
                        }
                    )
                    "payment" -> selectedInvoice?.let { invoice ->
                        PaymentDialog(
                            invoice = invoice,
                            onDismiss = { dialog = ""; selectedInvoice = null },
                            onSave = { amount, method, reference ->
                                lifecycleScope.launch {
                                    runCatching {
                                        billing.recordPayment(invoice.id, toPaise(amount), method, reference)
                                        dialog = ""
                                        selectedInvoice = null
                                        showToast("Payment recorded")
                                    }.onFailure { showToast(it.message ?: "Could not record payment") }
                                }
                            }
                        )
                    }
                    "reminder" -> selectedInvoice?.let { invoice ->
                        AlertDialog(
                            onDismissRequest = { dialog = ""; selectedInvoice = null },
                            title = { Text("Confirm payment reminder") },
                            text = { Text("Open WhatsApp with a prefilled reminder for ${invoice.customerName}, invoice ${invoice.number}, due ₹${money(invoice.duePaise)}? You must review and send it yourself.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    val phone = InvoiceSharing.normalizeIndianPhone(invoice.phone)
                                    if (phone.isBlank()) {
                                        showToast("Customer phone is missing or invalid")
                                    } else {
                                        lifecycleScope.launch {
                                            val settings = db.dao().settings() ?: ShopSettings()
                                            val body = "Payment reminder for invoice ${invoice.number}: outstanding ₹${money(invoice.duePaise)}. Please contact ${settings.name} ${settings.phone}."
                                            val url = "https://wa.me/$phone?text=" + URLEncoder.encode(body, "UTF-8")
                                            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                                .onFailure { showToast("Could not open WhatsApp or browser") }
                                            dialog = ""
                                            selectedInvoice = null
                                        }
                                    }
                                }) { Text("Continue to WhatsApp") }
                            },
                            dismissButton = {
                                TextButton(onClick = { dialog = ""; selectedInvoice = null }) { Text("Cancel") }
                            }
                        )
                    }
                }
                message?.let { Text(it) }
            }
        }
    }

    private fun showToast(text: String) =
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

@Composable
private fun CustomerDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add customer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Customer name *") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Address") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, phone, address) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ProductDialog(onDismiss: () -> Unit, onSave: (String, String, String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("0") }
    var lowStock by remember { mutableStateOf("3") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add product / service") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true)
                OutlinedTextField(sku, { sku = it }, label = { Text("SKU / code") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Selling price (₹)") }, singleLine = true)
                OutlinedTextField(stock, { stock = it }, label = { Text("Opening stock") }, singleLine = true)
                OutlinedTextField(lowStock, { lowStock = it }, label = { Text("Low-stock alert threshold") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = price.toBigDecimalOrNull()
                val s = stock.toIntOrNull()
                val l = lowStock.toIntOrNull()
                if (p == null || s == null || l == null) return@TextButton
                onSave(name, sku, p.toPlainString(), s, l)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BillDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, Int, String, String, String, String) -> Unit
) {
    var customer by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var rate by remember { mutableStateOf("") }
    var discount by remember { mutableStateOf("0") }
    var tax by remember { mutableStateOf("0") }
    var paid by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create invoice") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Quick invoice for a free-text item. Stock-linked checkout is a follow-up workflow.")
                OutlinedTextField(customer, { customer = it }, label = { Text("Customer name *") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Item / frame / lens *") }, singleLine = true)
                OutlinedTextField(qty, { qty = it }, label = { Text("Quantity") }, singleLine = true)
                OutlinedTextField(rate, { rate = it }, label = { Text("Unit rate (₹)") }, singleLine = true)
                OutlinedTextField(discount, { discount = it }, label = { Text("Line discount (₹)") }, singleLine = true)
                OutlinedTextField(tax, { tax = it }, label = { Text("Tax amount (₹)") }, singleLine = true)
                OutlinedTextField(paid, { paid = it }, label = { Text("Amount received (₹)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (qty.toIntOrNull() == null || listOf(rate, discount, tax, paid).any { it.toBigDecimalOrNull() == null }) return@TextButton
                onSave(customer, phone, description, qty.toInt(), rate, discount, tax, paid)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PaymentDialog(
    invoice: Invoice,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf("Cash") }
    var reference by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment · ${invoice.number}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Outstanding: ₹${money(invoice.duePaise)}")
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount received (₹)") }, singleLine = true)
                OutlinedTextField(method, { method = it }, label = { Text("Method (Cash / UPI / Card)") }, singleLine = true)
                OutlinedTextField(reference, { reference = it }, label = { Text("Reference (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (amount.toBigDecimalOrNull() == null || amount.toBigDecimalOrNull()!! <= java.math.BigDecimal.ZERO) return@TextButton
                onSave(amount, method, reference)
            }) { Text("Save payment") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun toPaise(amount: String): Long =
    ((amount.toBigDecimalOrNull() ?: error("Enter a valid amount")) * java.math.BigDecimal(100))
        .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()

private fun money(paise: Long): String = String.format(Locale.US, "%.2f", paise / 100.0)
