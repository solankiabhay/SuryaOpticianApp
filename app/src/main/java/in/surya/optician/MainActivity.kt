package com.surya.optician

import android.content.ClipData
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
import com.surya.optician.data.*
import com.surya.optician.export.BackupExporter
import com.surya.optician.invoice.InvoicePdf
import com.surya.optician.invoice.InvoiceSharing
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
                var shopSettings by remember { mutableStateOf(ShopSettings()) }
                LaunchedEffect(Unit) {
                    shopSettings = db.dao().settings() ?: ShopSettings().also { db.dao().saveSettings(it) }
                }
                val invoices by db.dao().searchInvoices(query).collectAsState(initial = emptyList())
                val customers by db.dao().customers().collectAsState(initial = emptyList())
                val products by db.dao().products().collectAsState(initial = emptyList())
                val due by db.dao().outstanding().collectAsState(initial = 0L)
                val sales by db.dao().salesTotal().collectAsState(initial = 0L)
                val collected by db.dao().collectedTotal().collectAsState(initial = 0L)
                val invoiceCount by db.dao().invoiceCount().collectAsState(initial = 0)
                val lowStock by db.dao().lowStock().collectAsState(initial = emptyList())

                Scaffold(topBar = { TopAppBar(title = { Text("Surya Optician") }) }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                        Text("Optical Shop Dashboard", style = MaterialTheme.typography.headlineSmall)
                        Text("Customers: ${customers.size}  Products: ${products.size}  Invoices: $invoiceCount")
                        Text("Sales: ₹${money(sales)} · Collected: ₹${money(collected)} · Outstanding: ₹${money(due)}")
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
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { dialog = "prescription" }) { Text("Add prescription") }
                            OutlinedButton(onClick = { dialog = "settings" }) { Text("Shop settings") }
                        }
                        OutlinedButton(
                            onClick = {
                                lifecycleScope.launch {
                                    runCatching {
                                        val file = BackupExporter.export(this@MainActivity, db.dao())
                                        val uri = FileProvider.getUriForFile(
                                            this@MainActivity, "${packageName}.fileprovider", file
                                        )
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            clipData = ClipData.newUri(contentResolver, "Surya Optician backup", uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        startActivity(Intent.createChooser(send, "Export backup"))
                                    }.onFailure { showToast(it.message ?: "Backup export failed") }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Export full backup (JSON)") }
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
                                            TextButton(onClick = {
                                                selectedInvoice = invoice
                                                dialog = "history"
                                            }) { Text("Payment history") }
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
                                    val pricePaise = toPaise(price)
                                    require(pricePaise >= 0 && stock >= 0 && lowStockThreshold >= 0) { "Price and stock cannot be negative" }
                                    db.dao().addProduct(Product(
                                        name = name.trim(), sku = sku.trim(),
                                        pricePaise = pricePaise, stock = stock,
                                        lowStock = lowStockThreshold
                                    ))
                                    dialog = ""
                                    showToast("Product saved")
                                }.onFailure { showToast(it.message ?: "Could not save product") }
                            }
                        }
                    )
                    "bill" -> BillDialog(
                        customers = customers,
                        products = products,
                        onDismiss = { dialog = "" },
                        onSave = { customerId, customerName, phone, drafts, paid ->
                            lifecycleScope.launch {
                                runCatching {
                                    require(customerName.isNotBlank()) { "Customer name is required" }
                                    require(drafts.isNotEmpty()) { "Add at least one item" }
                                    val lines = drafts.map { draft ->
                                        BillLine(
                                            productId = draft.productId,
                                            description = draft.description.trim(),
                                            qty = draft.qty.toIntOrNull() ?: error("Enter a valid quantity"),
                                            ratePaise = toPaise(draft.rate),
                                            discountPaise = toPaise(draft.discount),
                                            taxPaise = toPaise(draft.tax)
                                        )
                                    }
                                    val paidPaise = toPaise(paid)
                                    require(paidPaise >= 0) { "Payment cannot be negative" }
                                    billing.createBill(NewBill(
                                        customerId = customerId,
                                        customerName = customerName.trim(),
                                        phone = phone.trim(),
                                        lines = lines,
                                        paidPaise = paidPaise
                                    ))
                                    dialog = ""
                                    showToast("Invoice created")
                                }.onFailure { showToast(it.message ?: "Could not create invoice") }
                            }
                        }
                    )
                    "prescription" -> PrescriptionDialog(
                        customers = customers,
                        onDismiss = { dialog = "" },
                        onSave = { customerId, rs, rc, ra, rad, ls, lc, la, lad, pd, notes ->
                            lifecycleScope.launch {
                                runCatching {
                                    db.dao().addPrescription(Prescription(
                                        customerId = customerId, rightSphere = rs, rightCylinder = rc,
                                        rightAxis = ra, rightAdd = rad, leftSphere = ls, leftCylinder = lc,
                                        leftAxis = la, leftAdd = lad, pd = pd, notes = notes
                                    ))
                                    dialog = ""
                                    showToast("Prescription saved")
                                }.onFailure { showToast(it.message ?: "Could not save prescription") }
                            }
                        }
                    )
                    "settings" -> SettingsDialog(
                        initial = shopSettings,
                        onDismiss = { dialog = "" },
                        onSave = { settings ->
                            lifecycleScope.launch {
                                runCatching {
                                    db.dao().saveSettings(settings)
                                    shopSettings = settings
                                    dialog = ""
                                    showToast("Shop settings saved")
                                }.onFailure { showToast(it.message ?: "Could not save settings") }
                            }
                        }
                    )
                    "history" -> selectedInvoice?.let { invoice ->
                        PaymentHistoryDialog(
                            invoice = invoice,
                            paymentsFlow = db.dao().payments(invoice.id),
                            onDismiss = { dialog = ""; selectedInvoice = null }
                        )
                    }
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


private data class BillDraftLine(
    val productId: Long? = null,
    val description: String = "",
    val qty: String = "1",
    val rate: String = "",
    val discount: String = "0",
    val tax: String = "0"
)

@Composable
private fun BillDialog(
    customers: List<Customer>,
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, List<BillDraftLine>, String) -> Unit
) {
    var customer by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var customerMenu by remember { mutableStateOf(false) }
    val lines = remember { androidx.compose.runtime.mutableStateListOf(BillDraftLine()) }
    var expandedProductIndex by remember { mutableStateOf<Int?>(null) }
    var paid by remember { mutableStateOf("0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create invoice") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    OutlinedTextField(
                        value = customers.firstOrNull { it.id == selectedCustomerId }?.name ?: "",
                        onValueChange = {}, readOnly = true, label = { Text("Select saved customer (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { TextButton(onClick = { customerMenu = true }) { Text("Choose") } }
                    )
                    DropdownMenu(expanded = customerMenu, onDismissRequest = { customerMenu = false }) {
                        customers.forEach { saved ->
                            DropdownMenuItem(text = { Text(saved.name) }, onClick = {
                                selectedCustomerId = saved.id
                                customer = saved.name
                                phone = saved.phone
                                customerMenu = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    customer, { customer = it; selectedCustomerId = null },
                    label = { Text("Customer name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                lines.forEachIndexed { index, line ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Item ${index + 1}", style = MaterialTheme.typography.titleSmall)
                                if (lines.size > 1) {
                                    TextButton(onClick = { lines.removeAt(index) }) { Text("Remove") }
                                }
                            }
                            Box {
                                OutlinedTextField(
                                    value = products.firstOrNull { it.id == line.productId }?.name ?: "Free-text item",
                                    onValueChange = {}, readOnly = true,
                                    label = { Text("Choose stock product (optional)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        TextButton(onClick = { expandedProductIndex = index }) { Text("Choose") }
                                    }
                                )
                                DropdownMenu(
                                    expanded = expandedProductIndex == index,
                                    onDismissRequest = { expandedProductIndex = null }
                                ) {
                                    DropdownMenuItem(text = { Text("Free-text item (no stock tracking)") }, onClick = {
                                        lines[index] = line.copy(productId = null, description = "")
                                        expandedProductIndex = null
                                    })
                                    products.forEach { product ->
                                        DropdownMenuItem(
                                            text = { Text("${product.name} · stock ${product.stock}") },
                                            onClick = {
                                                lines[index] = line.copy(
                                                    productId = product.id,
                                                    description = product.name,
                                                    rate = String.format(Locale.US, "%.2f", product.pricePaise / 100.0)
                                                )
                                                expandedProductIndex = null
                                            }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                line.description,
                                { lines[index] = line.copy(productId = null, description = it) },
                                label = { Text("Item / frame / lens *") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    line.qty, { lines[index] = line.copy(qty = it) },
                                    label = { Text("Qty") }, modifier = Modifier.weight(0.7f), singleLine = true
                                )
                                OutlinedTextField(
                                    line.rate, { lines[index] = line.copy(rate = it) },
                                    label = { Text("Rate ₹") }, modifier = Modifier.weight(1.3f), singleLine = true
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    line.discount, { lines[index] = line.copy(discount = it) },
                                    label = { Text("Discount ₹") }, modifier = Modifier.weight(1f), singleLine = true
                                )
                                OutlinedTextField(
                                    line.tax, { lines[index] = line.copy(tax = it) },
                                    label = { Text("Tax ₹") }, modifier = Modifier.weight(1f), singleLine = true
                                )
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { lines.add(BillDraftLine()) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add another item") }
                OutlinedTextField(
                    paid, { paid = it }, label = { Text("Amount received (₹)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("Stock is deducted only for lines linked to saved products.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (customer.isBlank() || lines.any {
                    it.description.isBlank() || it.qty.toIntOrNull() == null ||
                        it.qty.toIntOrNull()!! <= 0 || it.rate.toBigDecimalOrNull() == null ||
                        it.discount.toBigDecimalOrNull() == null || it.tax.toBigDecimalOrNull() == null
                } || paid.toBigDecimalOrNull() == null) return@TextButton
                onSave(selectedCustomerId, customer, phone, lines.toList(), paid)
            }) { Text("Create invoice") }
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


@Composable
private fun PrescriptionDialog(
    customers: List<Customer>,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, String, String, String, String, String, String, String, String) -> Unit
) {
    var customerId by remember { mutableStateOf<Long?>(customers.firstOrNull()?.id) }
    var customerMenu by remember { mutableStateOf(false) }
    var rs by remember { mutableStateOf("") }
    var rc by remember { mutableStateOf("") }
    var ra by remember { mutableStateOf("") }
    var rad by remember { mutableStateOf("") }
    var ls by remember { mutableStateOf("") }
    var lc by remember { mutableStateOf("") }
    var la by remember { mutableStateOf("") }
    var lad by remember { mutableStateOf("") }
    var pd by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New prescription") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    OutlinedTextField(
                        value = customers.firstOrNull { it.id == customerId }?.name ?: "Select customer",
                        onValueChange = {}, readOnly = true, label = { Text("Customer") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { TextButton(onClick = { customerMenu = true }) { Text("Choose") } }
                    )
                    DropdownMenu(expanded = customerMenu, onDismissRequest = { customerMenu = false }) {
                        customers.forEach { customer ->
                            DropdownMenuItem(text = { Text(customer.name) }, onClick = {
                                customerId = customer.id
                                customerMenu = false
                            })
                        }
                    }
                }
                Text("Right eye (OD)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(rs, { rs = it }, label = { Text("SPH") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(rc, { rc = it }, label = { Text("CYL") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(ra, { ra = it }, label = { Text("AXIS") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(rad, { rad = it }, label = { Text("ADD") }, singleLine = true)
                Text("Left eye (OS)", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(ls, { ls = it }, label = { Text("SPH") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(lc, { lc = it }, label = { Text("CYL") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(la, { la = it }, label = { Text("AXIS") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(lad, { lad = it }, label = { Text("ADD") }, singleLine = true)
                OutlinedTextField(pd, { pd = it }, label = { Text("Pupillary distance (PD)") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val id = customerId ?: return@TextButton
                onSave(id, rs, rc, ra, rad, ls, lc, la, lad, pd, notes)
            }) { Text("Save prescription") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SettingsDialog(
    initial: ShopSettings,
    onDismiss: () -> Unit,
    onSave: (ShopSettings) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial.name) }
    var address by remember(initial) { mutableStateOf(initial.address) }
    var phone by remember(initial) { mutableStateOf(initial.phone) }
    var gstin by remember(initial) { mutableStateOf(initial.gstin) }
    var footer by remember(initial) { mutableStateOf(initial.footer) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shop settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Shop name") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Address") })
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true)
                OutlinedTextField(gstin, { gstin = it }, label = { Text("GSTIN") }, singleLine = true)
                OutlinedTextField(footer, { footer = it }, label = { Text("Invoice footer") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(initial.copy(
                    name = name.trim(), address = address.trim(), phone = phone.trim(),
                    gstin = gstin.trim(), footer = footer.trim()
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


@Composable
private fun PaymentHistoryDialog(
    invoice: Invoice,
    paymentsFlow: kotlinx.coroutines.flow.Flow<List<Payment>>,
    onDismiss: () -> Unit
) {
    val payments by paymentsFlow.collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Payments · \${invoice.number}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Invoice total ₹\${money(invoice.totalPaise)} · paid ₹\${money(invoice.paidPaise)} · due ₹\${money(invoice.duePaise)}")
                if (payments.isEmpty()) Text("No payments recorded.")
                payments.forEach { payment ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(8.dp)) {
                            Text("₹\${money(payment.amountPaise)} · \${payment.method}", style = MaterialTheme.typography.titleSmall)
                            if (payment.reference.isNotBlank()) Text("Reference: \${payment.reference}")
                            Text(java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(java.util.Date(payment.paidAt)))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
