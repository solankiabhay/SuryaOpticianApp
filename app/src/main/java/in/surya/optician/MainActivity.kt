package in.surya.optician

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import in.surya.optician.invoice.InvoiceCsv
import in.surya.optician.invoice.InvoiceSharing
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val db = OpticianDatabase.get(this)
  lifecycleScope.launch { if (db.dao().settings() == null) db.dao().saveSettings(ShopSettings()) }
  setContent {
   MaterialTheme {
    var section by remember { mutableStateOf("Bills") }
    var query by remember { mutableStateOf("") }
    var reportFrom by remember { mutableStateOf("") }
    var reportTo by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<Long?>(null) }
    var productName by remember { mutableStateOf("") }
    var productSku by remember { mutableStateOf("") }
    var productPrice by remember { mutableStateOf("") }
    var productStock by remember { mutableStateOf("1") }
    var productLowStock by remember { mutableStateOf("3") }
    var billCustomer by remember { mutableStateOf("") }
    var billCustomerId by remember { mutableStateOf("") }
    var billLines by remember { mutableStateOf(listOf<BillLine>()) }
    var billPhone by remember { mutableStateOf("") }
    var billProductId by remember { mutableStateOf("") }
    var billDescription by remember { mutableStateOf("") }
    var billQty by remember { mutableStateOf("1") }
    var billRate by remember { mutableStateOf("") }
    var billDiscount by remember { mutableStateOf("0") }
    var billTax by remember { mutableStateOf("0") }
    var billPaid by remember { mutableStateOf("0") }
    var paymentInvoiceId by remember { mutableStateOf("") }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cash") }
    var prescriptionCustomerId by remember { mutableStateOf("") }
    var rightSphere by remember { mutableStateOf("") }
    var rightCylinder by remember { mutableStateOf("") }
    var rightAxis by remember { mutableStateOf("") }
    var rightAdd by remember { mutableStateOf("") }
    var leftSphere by remember { mutableStateOf("") }
    var leftCylinder by remember { mutableStateOf("") }
    var leftAxis by remember { mutableStateOf("") }
    var leftAdd by remember { mutableStateOf("") }
    var pd by remember { mutableStateOf("") }
    var prescriptionNotes by remember { mutableStateOf("") }
    var reminderInvoice by remember { mutableStateOf<Invoice?>(null) }
    val invoices by db.dao().searchInvoices(query).collectAsState(initial = emptyList())
    val allInvoices by db.dao().invoices().collectAsState(initial = emptyList())
    val prescriptionsFlow = remember(prescriptionCustomerId) {
     prescriptionCustomerId.toLongOrNull()?.let { db.dao().prescriptions(it) } ?: flowOf(emptyList())
    }
    val savedPrescriptions by prescriptionsFlow.collectAsState(initial = emptyList())
    val customers by db.dao().customers().collectAsState(initial = emptyList())
    val products by db.dao().products().collectAsState(initial = emptyList())
    val due by db.dao().outstanding().collectAsState(initial = 0L)
    val lowStock by db.dao().lowStock().collectAsState(initial = emptyList())

    fun toast(message: String) = Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
    Scaffold(topBar = { TopAppBar(title = { Text("Surya Optician") }) }) { padding ->
     Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
       listOf("Bills", "Customers", "Products", "New Bill", "Payment", "Prescription", "Reports", "Settings").forEach { tab ->
        TextButton(onClick = { section = tab }) { Text(tab) }
       }
      }
      Text("Outstanding: ₹${"%.2f".format(due / 100.0)}  ·  Low stock: ${lowStock.size}", style = MaterialTheme.typography.titleSmall)
      Spacer(Modifier.height(8.dp))
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
      when (section) {
       "Customers" -> {
        OutlinedTextField(customerName, { customerName = it }, label = { Text("Customer name *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(customerPhone, { customerPhone = it }, label = { Text("Phone (WhatsApp)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(customerAddress, { customerAddress = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { lifecycleScope.launch {
         if (customerName.isBlank()) toast("Customer name is required")
         else if (selectedCustomerId == null) { db.dao().addCustomer(Customer(name=customerName.trim(), phone=customerPhone.trim(), address=customerAddress.trim())); customerName=""; customerPhone=""; customerAddress=""; toast("Customer saved") }
         else { db.dao().updateCustomer(Customer(id=selectedCustomerId!!, name=customerName.trim(), phone=customerPhone.trim(), address=customerAddress.trim())); selectedCustomerId=null; customerName=""; customerPhone=""; customerAddress=""; toast("Customer updated") }
        } }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedCustomerId == null) "Save customer" else "Update customer") }
        if (selectedCustomerId != null) TextButton(onClick={ selectedCustomerId=null; customerName=""; customerPhone=""; customerAddress="" }) { Text("Cancel customer edit") }
        Text("Tap a customer to edit their details.", style=MaterialTheme.typography.bodySmall)
        Column { customers.forEach { customer -> TextButton(onClick={ selectedCustomerId=customer.id; customerName=customer.name; customerPhone=customer.phone; customerAddress=customer.address }) { Text("#${customer.id} · ${customer.name} · ${customer.phone}") } } }
       }
       "Products" -> {
        OutlinedTextField(productName, { productName = it }, label = { Text("Product / frame / lens name *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productSku, { productSku = it }, label = { Text("SKU (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productPrice, { productPrice = it }, label = { Text("Selling price in ₹ *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productStock, { productStock = it }, label = { Text("Opening stock") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productLowStock, { productLowStock = it }, label = { Text("Low stock alert at") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { lifecycleScope.launch {
         val price = productPrice.toDoubleOrNull()
         val stock = productStock.toIntOrNull()
         val low = productLowStock.toIntOrNull()
         if (productName.isBlank() || price == null || price < 0 || stock == null || stock < 0 || low == null || low < 0) toast("Enter valid product, price and stock")
         else if (selectedProductId == null) { db.dao().addProduct(Product(name=productName.trim(), sku=productSku.trim(), pricePaise=(price*100).toLong(), stock=stock, lowStock=low)); productName=""; productSku=""; productPrice=""; productStock="1"; productLowStock="3"; toast("Product saved") }
         else { db.dao().updateProduct(Product(id=selectedProductId!!, name=productName.trim(), sku=productSku.trim(), pricePaise=(price*100).toLong(), stock=stock, lowStock=low)); selectedProductId=null; productName=""; productSku=""; productPrice=""; productStock="1"; productLowStock="3"; toast("Product and stock updated") }
        } }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedProductId == null) "Save product" else "Update product / stock") }
        if (selectedProductId != null) TextButton(onClick={ selectedProductId=null; productName=""; productSku=""; productPrice=""; productStock="1"; productLowStock="3" }) { Text("Cancel product edit") }
        Text("Tap a product to edit price, SKU, low-stock threshold or adjust stock.", style=MaterialTheme.typography.bodySmall)
        Column { products.forEach { product -> TextButton(onClick={ selectedProductId=product.id; productName=product.name; productSku=product.sku; productPrice="%.2f".format(product.pricePaise/100.0); productStock=product.stock.toString(); productLowStock=product.lowStock.toString() }) { Text("#${product.id} · ${product.name} · ₹${"%.2f".format(product.pricePaise/100.0)} · stock ${product.stock}") } } }
       }
       "New Bill" -> {
        Text("Create invoice", style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(billCustomerId, { billCustomerId=it }, label={ Text("Existing customer ID (optional)") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billCustomer, { billCustomer=it }, label={ Text("Customer name *") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billPhone, { billPhone=it }, label={ Text("Customer phone") }, modifier=Modifier.fillMaxWidth())
        Text("Products: ${products.joinToString { "#${it.id} ${it.name} (stock ${it.stock})" }}", style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(billProductId, { billProductId=it }, label={ Text("Product ID (blank for custom item)") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billDescription, { billDescription=it }, label={ Text("Item description *") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billQty, { billQty=it }, label={ Text("Quantity") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billRate, { billRate=it }, label={ Text("Unit rate in ₹") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billDiscount, { billDiscount=it }, label={ Text("Line discount in ₹") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billTax, { billTax=it }, label={ Text("Line tax in ₹") }, modifier=Modifier.fillMaxWidth())
        Button(onClick={
         val qty=billQty.toIntOrNull(); val rate=billRate.toDoubleOrNull(); val discount=billDiscount.toDoubleOrNull(); val tax=billTax.toDoubleOrNull(); val pid=billProductId.toLongOrNull()
         if(billDescription.isBlank() || qty==null || qty<=0 || rate==null || rate<0 || discount==null || discount<0 || tax==null || tax<0) toast("Check item description, quantity and amounts")
         else runCatching { BillLine(pid,billDescription.trim(),qty,(rate*100).toLong(),(discount*100).toLong(),(tax*100).toLong()) }
          .onSuccess { line -> billLines=billLines+line; billProductId=""; billDescription=""; billQty="1"; billRate=""; billDiscount="0"; billTax="0"; toast("Item added to bill") }
          .onFailure { toast(it.message ?: "Invalid item") }
        }, modifier=Modifier.fillMaxWidth()) { Text("Add item to bill") }
        Text("Items in cart: ${billLines.size} · Cart total ₹${"%.2f".format(billLines.sumOf { it.total }/100.0)}", style=MaterialTheme.typography.titleSmall)
        billLines.forEachIndexed { index, line ->
         Text("${index+1}. ${line.description} × ${line.qty} · ₹${"%.2f".format(line.total/100.0)}")
        }
        OutlinedTextField(billPaid, { billPaid=it }, label={ Text("Amount received in ₹") }, modifier=Modifier.fillMaxWidth())
        Button(onClick={ lifecycleScope.launch {
         val paid=billPaid.toDoubleOrNull()
         val cid=billCustomerId.toLongOrNull()
         val existingCustomer=if(cid!=null) db.dao().customer(cid) else null
         if((billCustomer.isBlank() && existingCustomer==null) || billLines.isEmpty() || paid==null || paid<0) toast("Enter/select a customer, add at least one item and check payment")
         else if(billCustomerId.isNotBlank() && existingCustomer==null) toast("Existing customer ID was not found")
         else runCatching {
          BillingRepository(db).createBill(NewBill(customerId=existingCustomer?.id, customerName=existingCustomer?.name ?: billCustomer.trim(), phone=existingCustomer?.phone?.ifBlank { billPhone.trim() } ?: billPhone.trim(), lines=billLines, paidPaise=(paid*100).toLong()))
         }.onSuccess { id -> toast("Invoice created: #$id"); section="Bills"; billCustomer=""; billCustomerId=""; billPhone=""; billLines=emptyList(); billProductId=""; billDescription=""; billQty="1"; billRate=""; billDiscount="0"; billTax="0"; billPaid="0" }
          .onFailure { toast(it.message ?: "Could not create invoice") }
        } }, modifier=Modifier.fillMaxWidth()) { Text("Create bill and deduct stock") }
       }
        "Payment" -> {
        OutlinedTextField(paymentInvoiceId, { paymentInvoiceId=it }, label={ Text("Invoice database ID") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(paymentAmount, { paymentAmount=it }, label={ Text("Payment amount in ₹") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(paymentMethod, { paymentMethod=it }, label={ Text("Method (Cash / UPI / Card / Bank)") }, modifier=Modifier.fillMaxWidth())
        Button(onClick={ lifecycleScope.launch {
         val id=paymentInvoiceId.toLongOrNull(); val amount=paymentAmount.toDoubleOrNull()
         if(id==null || amount==null || amount<=0) toast("Enter a valid invoice ID and payment amount")
         else runCatching { BillingRepository(db).recordPayment(id,(amount*100).toLong(),paymentMethod.trim().ifBlank{"Cash"}) }
          .onSuccess { toast("Payment recorded"); paymentAmount="" }.onFailure { toast(it.message ?: "Payment failed") }
        } }, modifier=Modifier.fillMaxWidth()) { Text("Record payment") }
        Text("Find the invoice database ID in the invoice list below.", style=MaterialTheme.typography.bodySmall)
        Column { invoices.forEach { Text("#${it.id} · ${it.number} · ${it.customerName} · due ₹${"%.2f".format(it.duePaise/100.0)}", Modifier.padding(8.dp)) } }
       }
       "Prescription" -> {
        Text("Eye prescription", style=MaterialTheme.typography.titleLarge)
        Text("Choose a customer ID from the customer list. Enter values exactly as prescribed by the optometrist.", style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(prescriptionCustomerId, { prescriptionCustomerId=it }, label={ Text("Customer ID *") }, modifier=Modifier.fillMaxWidth())
        customers.forEach { customer ->
         TextButton(onClick={ prescriptionCustomerId=customer.id.toString() }) { Text("Select #${customer.id} · ${customer.name} · ${customer.phone}") }
        }
        Text("Right eye (OD)", style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(rightSphere, { rightSphere=it }, label={ Text("SPH") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(rightCylinder, { rightCylinder=it }, label={ Text("CYL") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(rightAxis, { rightAxis=it }, label={ Text("Axis (0–180)") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(rightAdd, { rightAdd=it }, label={ Text("ADD") }, modifier=Modifier.fillMaxWidth())
        Text("Left eye (OS)", style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(leftSphere, { leftSphere=it }, label={ Text("SPH") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(leftCylinder, { leftCylinder=it }, label={ Text("CYL") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(leftAxis, { leftAxis=it }, label={ Text("Axis (0–180)") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(leftAdd, { leftAdd=it }, label={ Text("ADD") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(pd, { pd=it }, label={ Text("Pupillary distance (PD, mm)") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(prescriptionNotes, { prescriptionNotes=it }, label={ Text("Notes / lens recommendation") }, modifier=Modifier.fillMaxWidth(), minLines=2)
        Button(onClick={ lifecycleScope.launch {
         val cid=prescriptionCustomerId.toLongOrNull()
         val rightAxisValue=rightAxis.toIntOrNull()
         val leftAxisValue=leftAxis.toIntOrNull()
         if(cid==null || customers.none{it.id==cid}) toast("Select an existing customer")
         else if((rightAxis.isNotBlank() && (rightAxisValue==null || rightAxisValue !in 0..180)) || (leftAxis.isNotBlank() && (leftAxisValue==null || leftAxisValue !in 0..180))) toast("Axis must be a number from 0 to 180")
         else {
          db.dao().addPrescription(Prescription(customerId=cid,rightSphere=rightSphere.trim(),rightCylinder=rightCylinder.trim(),rightAxis=rightAxis.trim(),rightAdd=rightAdd.trim(),leftSphere=leftSphere.trim(),leftCylinder=leftCylinder.trim(),leftAxis=leftAxis.trim(),leftAdd=leftAdd.trim(),pd=pd.trim(),notes=prescriptionNotes.trim()))
          rightSphere="";rightCylinder="";rightAxis="";rightAdd="";leftSphere="";leftCylinder="";leftAxis="";leftAdd="";pd="";prescriptionNotes=""
          toast("Prescription saved")
         }
        } }, modifier=Modifier.fillMaxWidth()) { Text("Save prescription") }
        Text("Prescription history", style=MaterialTheme.typography.titleMedium)
        if(savedPrescriptions.isEmpty()) Text("No saved prescriptions for this customer yet.", style=MaterialTheme.typography.bodySmall)
        savedPrescriptions.forEach { rx ->
         Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
          Column(Modifier.padding(12.dp)) {
           Text(java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(rx.createdAt)), style=MaterialTheme.typography.titleSmall)
           Text("OD: SPH ${rx.rightSphere.ifBlank{"—"}}  CYL ${rx.rightCylinder.ifBlank{"—"}}  AXIS ${rx.rightAxis.ifBlank{"—"}}  ADD ${rx.rightAdd.ifBlank{"—"}}")
           Text("OS: SPH ${rx.leftSphere.ifBlank{"—"}}  CYL ${rx.leftCylinder.ifBlank{"—"}}  AXIS ${rx.leftAxis.ifBlank{"—"}}  ADD ${rx.leftAdd.ifBlank{"—"}}")
           Text("PD: ${rx.pd.ifBlank{"—"}} mm")
           if(rx.notes.isNotBlank()) Text("Notes: ${rx.notes}")
          }
         }
        }
       }
       "Reports" -> {
        Text("Shop reports", style=MaterialTheme.typography.titleLarge)
        val dateFormat = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { isLenient = false } }
        val fromMillis = remember(reportFrom) { if (reportFrom.isBlank()) null else runCatching { dateFormat.parse(reportFrom)?.time }.getOrNull() }
        val toMillisExclusive = remember(reportTo) { if (reportTo.isBlank()) null else runCatching { dateFormat.parse(reportTo)?.time?.plus(24L * 60 * 60 * 1000) }.getOrNull() }
        val invalidDateRange = (reportFrom.isNotBlank() && fromMillis == null) || (reportTo.isNotBlank() && toMillisExclusive == null) || (fromMillis != null && toMillisExclusive != null && fromMillis >= toMillisExclusive)
        val reportInvoices = if (invalidDateRange) emptyList() else allInvoices.filter { invoice -> (fromMillis == null || invoice.createdAt >= fromMillis) && (toMillisExclusive == null || invoice.createdAt < toMillisExclusive) }
        Text("Filter sales and collection totals by invoice date (YYYY-MM-DD). Leave blank to include all dates.", style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(reportFrom, { reportFrom = it.trim() }, label = { Text("From date (YYYY-MM-DD)") }, placeholder = { Text("2026-10-01") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(reportTo, { reportTo = it.trim() }, label = { Text("To date (YYYY-MM-DD)") }, placeholder = { Text("2026-10-31") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (invalidDateRange) Text("Enter valid dates in YYYY-MM-DD format and ensure From is not after To.", color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { reportFrom = ""; reportTo = "" }) { Text("Clear date filters") }
        Text("Invoice summary · ${reportInvoices.size} invoices in selected date range", style=MaterialTheme.typography.bodyMedium)
        val totalSales = reportInvoices.sumOf { it.totalPaise }
        val totalPaid = reportInvoices.sumOf { it.paidPaise }
        val totalDue = reportInvoices.sumOf { it.duePaise }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Invoices"); Text("${reportInvoices.size}", style=MaterialTheme.typography.headlineSmall) } }
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Sales"); Text("₹${"%.2f".format(totalSales/100.0)}", style=MaterialTheme.typography.titleMedium) } }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Collected"); Text("₹${"%.2f".format(totalPaid/100.0)}", style=MaterialTheme.typography.titleMedium) } }
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Outstanding"); Text("₹${"%.2f".format(totalDue/100.0)}", style=MaterialTheme.typography.titleMedium) } }
        }
        Button(onClick={ if (invalidDateRange) toast("Correct the date range before exporting") else InvoiceCsv.share(this@MainActivity, reportInvoices) }, enabled = !invalidDateRange, modifier=Modifier.fillMaxWidth()) { Text("Export date-filtered invoices to CSV") }
        Text("Customer invoice report", style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(query, { query=it }, label={Text("Filter by invoice number, customer or phone")}, modifier=Modifier.fillMaxWidth())
        val visibleReportInvoices = reportInvoices.filter { invoice -> query.isBlank() || invoice.number.contains(query, true) || invoice.customerName.contains(query, true) || invoice.phone.contains(query, true) }
        Text("${visibleReportInvoices.size} invoices match the date and search filters.", style = MaterialTheme.typography.bodySmall)
        visibleReportInvoices.forEach { invoice ->
         Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) {
          Column(Modifier.padding(10.dp)) {
           Text("${invoice.customerName} · ${invoice.number}", style=MaterialTheme.typography.titleSmall)
           Text("Date: "+java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(invoice.createdAt)))
           Text("Sales ₹${"%.2f".format(invoice.totalPaise/100.0)} · Paid ₹${"%.2f".format(invoice.paidPaise/100.0)} · Due ₹${"%.2f".format(invoice.duePaise/100.0)}")
          }
         }
        }
        Text("Note: totals reflect recorded invoice values; cost of goods is not stored, so this is not a profit report. Dates use the device local time zone.", style=MaterialTheme.typography.bodySmall)
       }
       "Settings" -> {
        var shopName by remember { mutableStateOf("Surya Optician") }
        var shopAddress by remember { mutableStateOf("") }
        var shopPhone by remember { mutableStateOf("") }
        var shopGstin by remember { mutableStateOf("") }
        var shopFooter by remember { mutableStateOf("Thank you for your purchase!") }
        LaunchedEffect(Unit) { db.dao().settings()?.let { shopName=it.name;shopAddress=it.address;shopPhone=it.phone;shopGstin=it.gstin;shopFooter=it.footer } }
        OutlinedTextField(shopName,{shopName=it},label={Text("Shop name")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(shopAddress,{shopAddress=it},label={Text("Shop address")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(shopPhone,{shopPhone=it},label={Text("Shop phone")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(shopGstin,{shopGstin=it},label={Text("GSTIN")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(shopFooter,{shopFooter=it},label={Text("Invoice footer")},modifier=Modifier.fillMaxWidth())
        Button(onClick={lifecycleScope.launch{db.dao().saveSettings(ShopSettings(name=shopName,address=shopAddress,phone=shopPhone,gstin=shopGstin,footer=shopFooter));toast("Shop settings saved")}},modifier=Modifier.fillMaxWidth()){Text("Save settings")}
       }
       else -> {
        OutlinedTextField(query, { query=it }, label={Text("Search invoice, customer, phone")}, modifier=Modifier.fillMaxWidth())
        Button(onClick={ InvoiceCsv.share(this@MainActivity, invoices) }, modifier=Modifier.fillMaxWidth()) { Text("Export visible invoices as CSV") }
        Column {
         invoices.forEach { invoice ->
          Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
           Column(Modifier.padding(12.dp)) {
            Text("${invoice.number} · ${invoice.customerName}", style=MaterialTheme.typography.titleMedium)
            Text("Total ₹${"%.2f".format(invoice.totalPaise/100.0)} · Paid ₹${"%.2f".format(invoice.paidPaise/100.0)} · Due ₹${"%.2f".format(invoice.duePaise/100.0)}")
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
             TextButton(onClick={lifecycleScope.launch{runCatching{val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings());val uri=FileProvider.getUriForFile(this@MainActivity,"${packageName}.fileprovider",file);startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{toast("PDF preview failed. Install a PDF viewer.")}}}){Text("Preview PDF")}
             Button(onClick={lifecycleScope.launch{runCatching{val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings());InvoiceSharing.share(this@MainActivity,file,invoice.phone,true)}.onFailure{toast("Invoice share failed")}}}){Text("WhatsApp PDF")}
            }
            if(invoice.duePaise>0) TextButton(onClick={reminderInvoice=invoice}){Text("Payment reminder")}
           }
          }
         }
        }
       }
      }
      }
     }
    }
    reminderInvoice?.let { invoice ->
     AlertDialog(onDismissRequest={reminderInvoice=null},title={Text("Send payment reminder?")},text={Text("Open WhatsApp with a reminder for ${invoice.customerName}, invoice ${invoice.number}, outstanding ₹${"%.2f".format(invoice.duePaise/100.0)}. You must review and send it in WhatsApp.")},confirmButton={TextButton(onClick={
      val selected=invoice;reminderInvoice=null
      lifecycleScope.launch{val phone=InvoiceSharing.normalizeIndianPhone(selected.phone);if(phone.isBlank())toast("Customer phone is missing or invalid") else {val settings=db.dao().settings()?:ShopSettings();val message="Payment reminder for invoice ${selected.number}: outstanding ₹${"%.2f".format(selected.duePaise/100.0)}. Please contact ${settings.name} ${settings.phone}.";startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/$phone?text="+URLEncoder.encode(message,"UTF-8"))))}}
     }){Text("Continue to WhatsApp")}},dismissButton={TextButton(onClick={reminderInvoice=null}){Text("Cancel")}})
    }
   }
  }
 }
}
