package `in`.surya.optician

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import `in`.surya.optician.data.*
import `in`.surya.optician.invoice.InvoicePdf
import `in`.surya.optician.invoice.InvoiceCsv
import `in`.surya.optician.invoice.InvoiceSharing
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import java.net.URLEncoder
import java.text.ParsePosition
import java.util.Calendar

private fun parseReportDate(value: String): Long? {
 if (!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return null
 val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
  isLenient = false
  timeZone = java.util.TimeZone.getDefault()
 }
 val position = ParsePosition(0)
 val parsed = formatter.parse(value, position) ?: return null
 if (position.index != value.length || formatter.format(parsed) != value) return null
 return parsed.time
}

private fun derivePinHash(pin: String, salt: ByteArray): ByteArray {
 val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
 return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
 private fun printPdf(file: File, jobName: String) {
  val manager = getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
  manager.print(jobName, object : PrintDocumentAdapter() {
   override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback?, extras: Bundle?) {
    if (cancellationSignal?.isCanceled == true) { callback?.onLayoutCancelled(); return }
    val info = PrintDocumentInfo.Builder(file.name)
     .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
     .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
     .build()
    callback?.onLayoutFinished(info, true)
   }
   override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor?, cancellationSignal: CancellationSignal?, callback: WriteResultCallback?) {
    if (cancellationSignal?.isCanceled == true) { callback?.onWriteCancelled(); return }
    try {
     val target = requireNotNull(destination) { "Print destination unavailable" }
     FileInputStream(file).use { input -> FileOutputStream(target.fileDescriptor).use { output -> input.copyTo(output) } }
     callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    } catch (error: Exception) {
     callback?.onWriteFailed(error.message ?: "Could not write PDF for printing")
    }
   }
  }, PrintAttributes.Builder().build())
 }
 private fun createPrescriptionPdf(rx: Prescription, customerName: String): File {
  val directory = File(filesDir, "prescriptions")
  check(directory.exists() || directory.mkdirs()) { "Cannot create prescription folder" }
  val file = File(directory, "prescription-${rx.id}.pdf")
  val document = PdfDocument()
  try {
   val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
   val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
   var y = 52f
   fun line(value: String, size: Float = 13f, bold: Boolean = false) {
    paint.textSize = size
    paint.typeface = android.graphics.Typeface.create("sans-serif", if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    page.canvas.drawText(value.ifBlank { "—" }.take(80), 40f, y, paint)
    y += size + 12f
   }
   line("SURYA OPTICIAN", 22f, true)
   line("EYE PRESCRIPTION", 18f, true)
   line("Customer: $customerName", 14f, true)
   line("Date: " + java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(rx.createdAt)))
   y += 8f
   line("RIGHT EYE (OD)", 15f, true)
   line("SPH: ${rx.rightSphere}   CYL: ${rx.rightCylinder}   Axis: ${rx.rightAxis}   ADD: ${rx.rightAdd}")
   line("LEFT EYE (OS)", 15f, true)
   line("SPH: ${rx.leftSphere}   CYL: ${rx.leftCylinder}   Axis: ${rx.leftAxis}   ADD: ${rx.leftAdd}")
   line("Pupillary distance (PD): ${rx.pd} mm")
   line("Notes: ${rx.notes}")
   line("This document records entered prescription details; confirm values with the prescribing optometrist.", 10f)
   document.finishPage(page)
   FileOutputStream(file).use { document.writeTo(it) }
  } finally { document.close() }
  return file
 }
 private fun sharePrescriptionPdf(rx: Prescription, customerName: String) {
  runCatching {
   val file = createPrescriptionPdf(rx, customerName)
   val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
   val intent = Intent(Intent.ACTION_SEND).apply {
    type = "application/pdf"
    putExtra(Intent.EXTRA_STREAM, uri)
    putExtra(Intent.EXTRA_SUBJECT, "Eye prescription - $customerName")
    clipData = android.content.ClipData.newUri(contentResolver, "Eye prescription", uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
   }
   startActivity(Intent.createChooser(intent, "Share prescription PDF"))
  }.onFailure { Toast.makeText(this, it.message ?: "Prescription PDF failed", Toast.LENGTH_LONG).show() }
 }
 private fun printPrescriptionPdf(rx: Prescription, customerName: String) {
  runCatching { printPdf(createPrescriptionPdf(rx, customerName), "Prescription - $customerName") }
   .onFailure { Toast.makeText(this, it.message ?: "Could not print prescription", Toast.LENGTH_LONG).show() }
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val db = OpticianDatabase.get(this)
  lifecycleScope.launch { if (db.dao().settings() == null) db.dao().saveSettings(ShopSettings()) }
  setContent {
   MaterialTheme(
    colorScheme = lightColorScheme(
     primary = androidx.compose.ui.graphics.Color(0xFF123A63),
     onPrimary = androidx.compose.ui.graphics.Color.White,
     primaryContainer = androidx.compose.ui.graphics.Color(0xFFDCEBFA),
     onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF102D4A),
     secondary = androidx.compose.ui.graphics.Color(0xFF2677A8),
     secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE2F2FC),
     onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF163D55),
     background = androidx.compose.ui.graphics.Color(0xFFF4F7FB),
     surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
     surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDF2F7),
     outline = androidx.compose.ui.graphics.Color(0xFFD5DEE8)
    ),
    shapes = Shapes(
     extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
     small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
     medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
     large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
     extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
    )
   ) {
    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/vnd.sqlite3")) { uri ->
     if (loggedInUser?.role != "Owner") { Toast.makeText(this@MainActivity, "Only the Owner can export a database backup.", Toast.LENGTH_LONG).show() }
     else if (uri != null) lifecycleScope.launch {
      runCatching {
       db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
       val databaseFile = getDatabasePath("surya-optician.db")
       check(databaseFile.exists()) { "Database file not found" }
       contentResolver.openOutputStream(uri)?.use { output -> FileInputStream(databaseFile).use { input -> input.copyTo(output) } } ?: error("Cannot write backup file")
      }.onSuccess { Toast.makeText(this@MainActivity, "Database backup exported. Keep it in a safe location.", Toast.LENGTH_LONG).show() }.onFailure { Toast.makeText(this@MainActivity, it.message ?: "Backup failed", Toast.LENGTH_LONG).show() }
     }
    }
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
     if (loggedInUser?.role != "Owner") { Toast.makeText(this@MainActivity, "Only the Owner can restore a backup.", Toast.LENGTH_LONG).show() }
     else if (uri != null) lifecycleScope.launch {
      val temp = File(cacheDir, "restore-validation.db")
      runCatching {
       contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(temp).use { output -> input.copyTo(output) } } ?: error("Cannot read selected backup")
       SQLiteDatabase.openDatabase(temp.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
        sqlite.rawQuery("PRAGMA integrity_check", null).use { cursor -> check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "Backup database integrity check failed" } }
        check(sqlite.version in 1..3) { "Unsupported database backup version" }
        sqlite.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('customers','products','invoices','invoice_items','payments','prescriptions','shop_settings')", null).use { cursor ->
         check(cursor.moveToFirst() && cursor.getInt(0) == 7) { "Selected file is not a complete Surya Optician database backup" }
        }
       }
       val databaseFile = getDatabasePath("surya-optician.db")
       OpticianDatabase.resetInstance()
       databaseFile.delete()
       File(databaseFile.absolutePath + "-wal").delete()
       File(databaseFile.absolutePath + "-shm").delete()
       check(temp.copyTo(databaseFile, overwrite = true).exists()) { "Could not restore database file" }
      }.onSuccess { temp.delete(); Toast.makeText(this@MainActivity, "Backup restored. Reopening app.", Toast.LENGTH_LONG).show(); recreate() }.onFailure { Toast.makeText(this@MainActivity, "Restore failed: ${it.message ?: "unknown error"}. A recovery copy was retained in app cache.", Toast.LENGTH_LONG).show() }
     }
    }
    val securityPrefs = remember { getSharedPreferences("surya_security", MODE_PRIVATE) }
    val accountStore = remember { UserAccounts(this@MainActivity) }
    var accountVersion by remember { mutableIntStateOf(0) }
    var loggedInUser by remember { mutableStateOf<LocalUser?>(null) }
    var authName by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var authConfirmPassword by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf("") }
    var newStaffName by remember { mutableStateOf("") }
    var newStaffPassword by remember { mutableStateOf("") }
    var newStaffRole by remember { mutableStateOf("Staff") }
    var unlocked by remember { mutableStateOf(securityPrefs.getString("pin_hash", null) == null) }
    var unlockPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("Dashboard") }
    var query by remember { mutableStateOf("") }
    var customerSearch by remember { mutableStateOf("") }
    var productSearch by remember { mutableStateOf("") }
    var editingPrescriptionId by remember { mutableStateOf<Long?>(null) }
    var reportFrom by remember { mutableStateOf("") }
    var reportTo by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var customerAge by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf<Long?>(null) }
    var productName by remember { mutableStateOf("") }
    var productSku by remember { mutableStateOf("") }
    var productPrice by remember { mutableStateOf("") }
    var productCost by remember { mutableStateOf("0") }
    var productCategory by remember { mutableStateOf("") }
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
    var billPaymentMethod by remember { mutableStateOf("Cash") }
    var billPrescriptionId by remember { mutableStateOf("") }
    var billPrescriptionSummary by remember { mutableStateOf("") }
    var billGlassType by remember { mutableStateOf("") }
    var billDeliveryDate by remember { mutableStateOf("") }
    var showBillPreview by remember { mutableStateOf(false) }
    var editingInvoiceId by remember { mutableStateOf<Long?>(null) }
    var invoiceDeleteTarget by remember { mutableStateOf<Invoice?>(null) }
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
     prescriptionCustomerId.toLongOrNull()?.let { db.dao().prescriptions(it) } ?: flowOf<List<Prescription>>(emptyList())
    }
    val savedPrescriptions by prescriptionsFlow.collectAsState(initial = emptyList())
    val billPrescriptionsFlow = remember(billCustomerId) { billCustomerId.toLongOrNull()?.let { db.dao().prescriptions(it) } ?: flowOf<List<Prescription>>(emptyList()) }
    val billPrescriptions by billPrescriptionsFlow.collectAsState(initial = emptyList())
    val selectedBillPrescription = billPrescriptions.firstOrNull { it.id.toString() == billPrescriptionId }
    val paymentHistoryFlow = remember(paymentInvoiceId) { paymentInvoiceId.toLongOrNull()?.let { db.dao().payments(it) } ?: flowOf<List<Payment>>(emptyList()) }
    val paymentHistory by paymentHistoryFlow.collectAsState(initial = emptyList())
    val customers by db.dao().customers().collectAsState(initial = emptyList())
    val products by db.dao().products().collectAsState(initial = emptyList())
    val due by db.dao().outstanding().collectAsState(initial = 0L)
    val lowStock by db.dao().lowStock().collectAsState(initial = emptyList())

    fun toast(message: String) = Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
    val currentRole = loggedInUser?.role ?: "Staff"
    fun isOwner() = currentRole == "Owner"
    fun canManageRecords() = currentRole == "Owner" || currentRole == "Manager"
    fun canDeleteRecords() = isOwner()
    fun canAccess(tab: String): Boolean = when (tab) {
     "Users" -> isOwner()
     "Reports" -> currentRole != "Staff"
     else -> true
    }
    fun denyPermission(action: String) { toast("Permission denied: only the Owner can $action.") }
    if (loggedInUser == null) {
     val needsOwnerSetup = accountStore.loadUsers().isEmpty()
     Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
      Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
       Text("SURYA OPTICIAN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
       Spacer(Modifier.height(8.dp))
       Text(if (needsOwnerSetup) "Set up your shop account" else "Welcome back", style = MaterialTheme.typography.headlineMedium)
       Spacer(Modifier.height(8.dp))
       Text(if (needsOwnerSetup) "Create the owner account to protect billing, customers and shop records." else "Sign in with your staff account to continue.", style = MaterialTheme.typography.bodyLarge)
       Spacer(Modifier.height(20.dp))
       OutlinedTextField(authName, { authName=it; authError="" }, label={Text(if(needsOwnerSetup) "Owner username" else "Username")}, singleLine=true, modifier=Modifier.fillMaxWidth())
       OutlinedTextField(authPassword, { authPassword=it; authError="" }, label={Text("Password")}, singleLine=true, modifier=Modifier.fillMaxWidth())
       if (needsOwnerSetup) OutlinedTextField(authConfirmPassword, { authConfirmPassword=it; authError="" }, label={Text("Confirm password")}, singleLine=true, modifier=Modifier.fillMaxWidth())
       if (authError.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(authError, color=MaterialTheme.colorScheme.error) }
       Spacer(Modifier.height(16.dp))
       Button(onClick={
        val username=authName.trim()
        if(username.length<3) authError="Username must be at least 3 characters."
        else if(authPassword.length<8) authError="Password must be at least 8 characters."
        else if(needsOwnerSetup && authPassword!=authConfirmPassword) authError="Passwords do not match."
        else if(needsOwnerSetup) runCatching { accountStore.create(username,authPassword,"Owner") }
         .onSuccess { accountVersion++; loggedInUser=accountStore.authenticate(username,authPassword); authPassword="";authConfirmPassword="";section="Dashboard" }
         .onFailure { authError=it.message ?: "Could not create owner account." }
        else {
         val user=accountStore.authenticate(username,authPassword)
         if(user==null) authError="Incorrect username or password."
         else { loggedInUser=user;authPassword="";authError="";section="Dashboard" }
        }
       }, modifier=Modifier.fillMaxWidth()) { Text(if(needsOwnerSetup) "Create owner account" else "Sign in") }
       Spacer(Modifier.height(12.dp))
       Text("Accounts are stored on this device. Keep a backup and do not share your password.", style=MaterialTheme.typography.bodySmall)
      }
     }
    } else {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
     TopAppBar(
      title = { Column { Text("Surya Optician"); Text("Billing & shop management", style=MaterialTheme.typography.labelSmall) } },
      actions = { TextButton(onClick={ loggedInUser=null;authName="";authPassword="";section="Dashboard" }) { Text("Sign out") } }
     )
    }) { padding ->
     Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
       (listOf("Dashboard", "Bills", "New Bill", "Customers", "Products", "Payment", "Prescription", "Reports", "Settings") + if(isOwner()) listOf("Users") else emptyList()).filter { canAccess(it) }.forEach { tab ->
        if(section==tab) Button(onClick={section=tab}, contentPadding=PaddingValues(horizontal=12.dp,vertical=8.dp)) { Text(tab) }
        else TextButton(onClick = { section = tab }) { Text(tab) }
       }
      }
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
       Card(Modifier.weight(1f), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(12.dp)) {
        Text("Outstanding", style=MaterialTheme.typography.labelMedium)
        Text("₹${"%.2f".format(due/100.0)}", style=MaterialTheme.typography.titleLarge)
       } }
       Card(Modifier.weight(1f), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) { Column(Modifier.padding(12.dp)) {
        Text("Low stock items", style=MaterialTheme.typography.labelMedium)
        Text("${lowStock.size}", style=MaterialTheme.typography.titleLarge)
       } }
      }
      Spacer(Modifier.height(8.dp))
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
      when (section) {
       "Dashboard" -> {
        Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
         Column(Modifier.padding(18.dp)) {
          Text("SURYA OPTICIAN", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.onPrimaryContainer)
          Spacer(Modifier.height(6.dp))
          Text("Good day, ${loggedInUser?.username}", style=MaterialTheme.typography.headlineSmall, color=MaterialTheme.colorScheme.onPrimaryContainer)
          Text("Your shop at a glance", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onPrimaryContainer)
          Spacer(Modifier.height(12.dp))
          Button(onClick={section="New Bill"}, modifier=Modifier.fillMaxWidth()) { Text("+ Create a new bill") }
         }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Invoices",style=MaterialTheme.typography.labelMedium); Text("${allInvoices.size}",style=MaterialTheme.typography.headlineSmall) } }
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Customers",style=MaterialTheme.typography.labelMedium); Text("${customers.size}",style=MaterialTheme.typography.headlineSmall) } }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Products",style=MaterialTheme.typography.labelMedium); Text("${products.size}",style=MaterialTheme.typography.headlineSmall) } }
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Low stock",style=MaterialTheme.typography.labelMedium); Text("${lowStock.size}",style=MaterialTheme.typography.headlineSmall) } }
        }
        Spacer(Modifier.height(16.dp))
        Text("Quick actions", style=MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
         OutlinedButton(onClick={section="Customers"}, modifier=Modifier.weight(1f)) { Text("Customers") }
         OutlinedButton(onClick={section="Products"}, modifier=Modifier.weight(1f)) { Text("Inventory") }
        }
        OutlinedButton(onClick={section="Payment"}, modifier=Modifier.fillMaxWidth()) { Text("Collect a payment") }
        Spacer(Modifier.height(12.dp))
        Text("Recent bills", style=MaterialTheme.typography.titleMedium)
        allInvoices.take(5).forEach { invoice ->
         Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) { Column(Modifier.padding(12.dp)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) { Text(invoice.number,style=MaterialTheme.typography.titleSmall); Text("₹${"%.2f".format(invoice.totalPaise/100.0)}",style=MaterialTheme.typography.titleSmall) }
          Text(invoice.customerName,style=MaterialTheme.typography.bodyMedium)
          Text(if(invoice.duePaise>0) "Due ₹${"%.2f".format(invoice.duePaise/100.0)}" else "Paid",style=MaterialTheme.typography.bodySmall)
         } }
        }
       }
       "Customers" -> {
        Text("Customer details", style=MaterialTheme.typography.titleLarge)
        Text("Keep contact details and age together for better customer service.", style=MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(customerName, { customerName = it }, label = { Text("Customer name *") }, modifier = Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(customerPhone, { customerPhone = it }, label = { Text("Phone (WhatsApp)") }, modifier = Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(customerAge, { customerAge = it.filter(Char::isDigit).take(3) }, label = { Text("Age (years, optional)") }, modifier = Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(customerAddress, { customerAddress = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if(!canManageRecords()) { denyPermission("edit customer records") } else lifecycleScope.launch {
         val ageValidation = validateCustomerAge(customerAge)
         val ageValue = ageValidation.age
         if (customerName.isBlank()) toast("Customer name is required")
         else if (!ageValidation.isValid) toast("Enter a valid age between 0 and 120")
         else if (selectedCustomerId == null) {
          db.dao().addCustomer(Customer(name=customerName.trim(), phone=customerPhone.trim(), address=customerAddress.trim(), age=ageValue))
          customerName=""; customerPhone=""; customerAddress=""; customerAge=""; toast("Customer saved")
         } else {
          db.dao().updateCustomer(Customer(id=selectedCustomerId!!, name=customerName.trim(), phone=customerPhone.trim(), address=customerAddress.trim(), age=ageValue))
          selectedCustomerId=null; customerName=""; customerPhone=""; customerAddress=""; customerAge=""; toast("Customer updated")
         }
        } }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedCustomerId == null) "Save customer" else "Update customer") }
        if (selectedCustomerId != null) TextButton(onClick={ selectedCustomerId=null; customerName=""; customerPhone=""; customerAddress=""; customerAge="" }) { Text("Cancel customer edit") }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(customerSearch, { customerSearch = it }, label={Text("Search customers by name or phone")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
        Text("Tap Edit to load a customer. Delete is disabled when invoices or prescriptions reference the customer.", style=MaterialTheme.typography.bodySmall)
        Column { customers.filter { customerSearch.isBlank() || it.name.contains(customerSearch,true) || it.phone.contains(customerSearch,true) }.forEach { customer ->
         Card(Modifier.fillMaxWidth().padding(vertical=4.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(12.dp)) {
          Text(customer.name, style=MaterialTheme.typography.titleMedium)
          Text(listOfNotNull(customer.phone.takeIf { it.isNotBlank() }, customer.age?.let { "Age $it years" }, customer.address.takeIf { it.isNotBlank() }).joinToString("  ·  ").ifBlank { "No additional details" }, style=MaterialTheme.typography.bodyMedium)
          Row {
           if(canManageRecords()) TextButton(onClick={ selectedCustomerId=customer.id; customerName=customer.name; customerPhone=customer.phone; customerAddress=customer.address; customerAge=customer.age?.toString() ?: "" }) { Text("Edit") }
           if(canDeleteRecords()) TextButton(onClick={ if(!canDeleteRecords()) denyPermission("delete customers") else lifecycleScope.launch {
            val hasInvoices=db.dao().customerInvoiceCount(customer.id)>0
            val hasPrescriptions=db.dao().customerPrescriptionCount(customer.id)>0
            if(hasInvoices || hasPrescriptions) toast("Cannot delete: customer has invoice or prescription history")
            else runCatching { db.dao().deleteCustomer(customer) }.onSuccess { toast("Customer deleted") }.onFailure { toast("Could not delete customer") }
           } }) { Text("Delete") }
          }
         } }
        } }
       }
       "Products" -> {
        OutlinedTextField(productName, { productName = it }, label = { Text("Product / frame / lens name *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productSku, { productSku = it }, label = { Text("SKU (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productPrice, { productPrice = it }, label = { Text("Selling price in ₹ *") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productCost, { productCost = it }, label = { Text("Purchase cost per unit in ₹ (for profit reports)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productCategory, { productCategory = it }, label = { Text("Category (Frame / Lens / Sunglasses / Other)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productStock, { productStock = it }, label = { Text("Opening stock") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(productLowStock, { productLowStock = it }, label = { Text("Low stock alert at") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if(!canManageRecords()) { denyPermission("manage inventory") } else lifecycleScope.launch {
         val price = productPrice.toDoubleOrNull()
         val cost = productCost.toDoubleOrNull()
         val stock = productStock.toIntOrNull()
         val low = productLowStock.toIntOrNull()
         if (productName.isBlank() || price == null || price < 0 || cost == null || cost < 0 || stock == null || stock < 0 || low == null || low < 0) toast("Enter valid product, selling/purchase price and stock")
         else if (selectedProductId == null) { db.dao().addProduct(Product(name=productName.trim(), sku=productSku.trim(), pricePaise=(price*100).toLong(), stock=stock, lowStock=low, costPaise=(cost*100).toLong(), category=productCategory.trim())); productName=""; productSku=""; productPrice=""; productCost="0"; productCategory=""; productStock="1"; productLowStock="3"; toast("Product saved") }
         else { db.dao().updateProduct(Product(id=selectedProductId!!, name=productName.trim(), sku=productSku.trim(), pricePaise=(price*100).toLong(), stock=stock, lowStock=low, costPaise=(cost*100).toLong(), category=productCategory.trim())); selectedProductId=null; productName=""; productSku=""; productPrice=""; productCost="0"; productCategory=""; productStock="1"; productLowStock="3"; toast("Product and stock updated") }
        } }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedProductId == null) "Save product" else "Update product / stock") }
        if (selectedProductId != null) TextButton(onClick={ selectedProductId=null; productName=""; productSku=""; productPrice=""; productCost="0"; productCategory=""; productStock="1"; productLowStock="3" }) { Text("Cancel product edit") }
        OutlinedTextField(productSearch, { productSearch=it }, label={Text("Search products by name, SKU or category")}, modifier=Modifier.fillMaxWidth())
        Text("Tap Edit to change product details. Products used on invoices cannot be deleted.", style=MaterialTheme.typography.bodySmall)
        Column { products.filter { productSearch.isBlank() || it.name.contains(productSearch,true) || it.sku.contains(productSearch,true) || it.category.contains(productSearch,true) }.forEach { product ->
         Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) { Column(Modifier.padding(8.dp)) {
          Text("#${product.id} · ${product.name} · Sell ₹${"%.2f".format(product.pricePaise/100.0)} · Cost ₹${"%.2f".format(product.costPaise/100.0)} · ${product.category.ifBlank { "Uncategorised" }} · stock ${product.stock}")
          Row {
           if(canManageRecords()) TextButton(onClick={ selectedProductId=product.id; productName=product.name; productSku=product.sku; productPrice="%.2f".format(product.pricePaise/100.0); productCost="%.2f".format(product.costPaise/100.0); productCategory=product.category; productStock=product.stock.toString(); productLowStock=product.lowStock.toString() }) { Text("Edit") }
           if(canDeleteRecords()) TextButton(onClick={ if(!canDeleteRecords()) denyPermission("delete products") else lifecycleScope.launch {
            if(db.dao().productInvoiceItemCount(product.id)>0) toast("Cannot delete: product appears on invoice history")
            else runCatching { db.dao().deleteProduct(product) }.onSuccess { toast("Product deleted") }.onFailure { toast("Could not delete product") }
           } }) { Text("Delete") }
          }
         } }
        } }
       }
       "New Bill" -> {
        Text(if(editingInvoiceId==null) "Create a complete bill" else "Edit existing bill", style=MaterialTheme.typography.headlineSmall)
        Text("Build an accurate invoice, attach a prescription if needed, and record the deposit.", style=MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
         Column(Modifier.padding(14.dp)) {
          Text(if(billLines.isEmpty()) "BILL SUMMARY" else "${billLines.size} ${if(billLines.size==1) "item" else "items"} added", style=MaterialTheme.typography.labelLarge)
          Spacer(Modifier.height(4.dp))
          Text("₹${"%.2f".format(billLines.sumOf { it.total }/100.0)}", style=MaterialTheme.typography.headlineMedium, color=MaterialTheme.colorScheme.primary)
          Text(if(billLines.isEmpty()) "Add a product or custom item to begin." else "Live total · taxes and item discounts included", style=MaterialTheme.typography.bodySmall)
         }
        }
        OutlinedTextField(billCustomerId, { billCustomerId=it; billPrescriptionId="" }, label={ Text("Existing customer ID (optional)") }, modifier=Modifier.fillMaxWidth())
        customers.forEach { customer -> TextButton(onClick={ billCustomerId=customer.id.toString(); billCustomer=customer.name; billPhone=customer.phone; billPrescriptionId="" }) { Text("Use #${customer.id} · ${customer.name} · ${customer.phone}") } }
        OutlinedTextField(billCustomer, { billCustomer=it }, label={ Text("Customer name *") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billPhone, { billPhone=it }, label={ Text("Customer phone") }, modifier=Modifier.fillMaxWidth())
        Text("Glass / lens type", style=MaterialTheme.typography.titleMedium)
        Text("Choose the main lens or product type for the customer’s order.", style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(billGlassType, { billGlassType=it }, label={ Text("Glass type (e.g. Single Vision, Progressive, Sunglasses)") }, modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
         listOf("Single Vision","Bifocal","Progressive","Photochromic","Blue Cut","Sunglasses","Frame Only","Contact Lens").forEach { type ->
          TextButton(onClick={billGlassType=type}) { Text(type, style=MaterialTheme.typography.labelSmall) }
         }
        }
        OutlinedTextField(billDeliveryDate, { billDeliveryDate=it }, label={ Text("Expected delivery date (DD/MM/YYYY)") }, placeholder={ Text("e.g. 05/11/2026") }, supportingText={ Text("Optional for walk-in sales; fill for orders awaiting lens fitting.") }, modifier=Modifier.fillMaxWidth())
        if (billCustomerId.toLongOrNull() != null) {
         Text("Attach eye prescription (optional)", style=MaterialTheme.typography.titleMedium)
         if (billPrescriptions.isEmpty()) Text("No saved prescription for this customer. You can still bill sunglasses or other products.", style=MaterialTheme.typography.bodySmall)
         billPrescriptions.forEach { rx -> TextButton(onClick={ billPrescriptionId=rx.id.toString() }) { Text((if (billPrescriptionId == rx.id.toString()) "✓ " else "") + "Prescription · " + java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(rx.createdAt))) } }
         selectedBillPrescription?.let { rx -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
          Text("Prescription attached to this bill", style=MaterialTheme.typography.titleSmall)
          Text("Right eye (OD): SPH ${rx.rightSphere.ifBlank{"—"}} · CYL ${rx.rightCylinder.ifBlank{"—"}} · Axis ${rx.rightAxis.ifBlank{"—"}} · ADD ${rx.rightAdd.ifBlank{"—"}}")
          Text("Left eye (OS): SPH ${rx.leftSphere.ifBlank{"—"}} · CYL ${rx.leftCylinder.ifBlank{"—"}} · Axis ${rx.leftAxis.ifBlank{"—"}} · ADD ${rx.leftAdd.ifBlank{"—"}}")
          Text("PD: ${rx.pd.ifBlank{"—"}}", style=MaterialTheme.typography.bodySmall)
          if(rx.notes.isNotBlank()) Text("Notes: ${rx.notes}", style=MaterialTheme.typography.bodySmall)
          TextButton(onClick={billPrescriptionId=""}) { Text("Remove prescription") }
         } } }
        }
        Text("Tap a product below to fill item details, then adjust quantity/discount/tax. Sunglasses and non-prescription products work the same way.", style=MaterialTheme.typography.bodySmall)
        products.forEach { product -> TextButton(onClick={ billProductId=product.id.toString(); billDescription=product.name; billRate="%.2f".format(product.pricePaise/100.0) }) { Text("#${product.id} · ${product.name} · ₹${"%.2f".format(product.pricePaise/100.0)} · stock ${product.stock}") } }
        OutlinedTextField(billProductId, { billProductId=it }, label={ Text("Product ID (blank for custom item)") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billDescription, { billDescription=it }, label={ Text("Item description *") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billQty, { billQty=it }, label={ Text("Quantity") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billRate, { billRate=it }, label={ Text("Unit rate in ₹") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billDiscount, { billDiscount=it }, label={ Text("Line discount in ₹") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(billTax, { billTax=it }, label={ Text("Line tax in ₹") }, modifier=Modifier.fillMaxWidth())
        Button(onClick={
         val qty=billQty.toIntOrNull(); val rate=billRate.toDoubleOrNull(); val discount=billDiscount.toDoubleOrNull(); val tax=billTax.toDoubleOrNull(); val pid=billProductId.toLongOrNull()
         if(billDescription.isBlank() || qty==null || qty<=0 || rate==null || rate<0 || discount==null || discount<0 || tax==null || tax<0 || (billProductId.isNotBlank() && pid==null)) toast("Check item description, product ID, quantity and amounts")
         else runCatching { BillLine(pid,billDescription.trim(),qty,(rate*100).toLong(),(discount*100).toLong(),(tax*100).toLong()) }
          .onSuccess { line -> billLines=billLines+line; billProductId=""; billDescription=""; billQty="1"; billRate=""; billDiscount="0"; billTax="0"; toast("Item added to bill") }
          .onFailure { toast(it.message ?: "Invalid item") }
        }, modifier=Modifier.fillMaxWidth()) { Text("Add item to bill") }
        Text("Items in cart: ${billLines.size} · Cart total ₹${"%.2f".format(billLines.sumOf { it.total }/100.0)}", style=MaterialTheme.typography.titleSmall)
        billLines.forEachIndexed { index, line ->
         Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
          Text("${index+1}. ${line.description} × ${line.qty} · ₹${"%.2f".format(line.total/100.0)}", modifier=Modifier.weight(1f))
          TextButton(onClick={ billLines=billLines.filterIndexed { i, _ -> i != index } }) { Text("Remove") }
         }
        }
        val draftSubtotal = billLines.sumOf { it.subtotalPaise }
        val draftDiscount = billLines.sumOf { it.discountPaise }
        val draftTax = billLines.sumOf { it.taxPaise }
        val draftTotal = billLines.sumOf { it.total }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
         Text("Live bill preview", style=MaterialTheme.typography.titleMedium)
         Text("Items subtotal: ₹${"%.2f".format(draftSubtotal/100.0)}")
         Text("Discount: ₹${"%.2f".format(draftDiscount/100.0)} · Tax: ₹${"%.2f".format(draftTax/100.0)}")
         Text("Grand total: ₹${"%.2f".format(draftTotal/100.0)}", style=MaterialTheme.typography.titleLarge)
         Text("Balance after payment: ₹${"%.2f".format((draftTotal-(billPaid.toDoubleOrNull()?.times(100)?.toLong() ?: 0L)).coerceAtLeast(0)/100.0)}")
        } }
        OutlinedTextField(billPaid, { billPaid=it }, label={ Text("Amount received in ₹") }, modifier=Modifier.fillMaxWidth())
        Text("Payment type", style=MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp), modifier=Modifier.fillMaxWidth()) {
         listOf("Cash", "Online", "Other").forEach { method ->
          if (billPaymentMethod == method) Button(onClick={ billPaymentMethod=method }, modifier=Modifier.weight(1f)) { Text(method) }
          else OutlinedButton(onClick={ billPaymentMethod=method }, modifier=Modifier.weight(1f)) { Text(method) }
         }
        }
        OutlinedButton(onClick={ showBillPreview=true }, modifier=Modifier.fillMaxWidth(), enabled=billLines.isNotEmpty()) { Text("Preview bill before saving") }
        Button(onClick={ lifecycleScope.launch {
         val paid=billPaid.toDoubleOrNull()
         val cid=billCustomerId.toLongOrNull()
         val existingCustomer=if(cid!=null) db.dao().customer(cid) else null
         if((billCustomer.isBlank() && existingCustomer==null) || billLines.isEmpty() || paid==null || paid<0) toast("Enter/select a customer, add at least one item and check payment")
         else if(billCustomerId.isNotBlank() && existingCustomer==null) toast("Existing customer ID was not found")
         else runCatching {
          run { val draft = NewBill(customerId=existingCustomer?.id, customerName=existingCustomer?.name ?: billCustomer.trim(), phone=existingCustomer?.phone?.ifBlank { billPhone.trim() } ?: billPhone.trim(), lines=billLines, paidPaise=(paid*100).toLong(), method=billPaymentMethod, glassType=billGlassType.trim(), deliveryDate=billDeliveryDate.trim(), prescriptionSummary=selectedBillPrescription?.let { rx -> listOf("Right eye (OD): SPH ${rx.rightSphere.ifBlank{"—"}}, CYL ${rx.rightCylinder.ifBlank{"—"}}, Axis ${rx.rightAxis.ifBlank{"—"}}, ADD ${rx.rightAdd.ifBlank{"—"}}", "Left eye (OS): SPH ${rx.leftSphere.ifBlank{"—"}}, CYL ${rx.leftCylinder.ifBlank{"—"}}, Axis ${rx.leftAxis.ifBlank{"—"}}, ADD ${rx.leftAdd.ifBlank{"—"}}", "PD: ${rx.pd.ifBlank{"—"}}", rx.notes.takeIf { it.isNotBlank() }?.let { "Notes: $it" }).filterNotNull().joinToString("\n") } ?: billPrescriptionSummary); if(editingInvoiceId==null) BillingRepository(db).createBill(draft) else { BillingRepository(db).updateBill(editingInvoiceId!!,draft); editingInvoiceId!! } }
         }.onSuccess { id -> toast(if(editingInvoiceId==null) "Invoice created: #$id" else "Invoice updated"); section="Bills"; editingInvoiceId=null; billCustomer=""; billCustomerId=""; billPhone=""; billLines=emptyList(); billProductId=""; billDescription=""; billQty="1"; billRate=""; billDiscount="0"; billTax="0"; billPaid="0"; billPaymentMethod="Cash"; billPrescriptionId=""; billPrescriptionSummary=""; billGlassType=""; billDeliveryDate="" }
          .onFailure { toast(it.message ?: "Could not create invoice") }
        } }, modifier=Modifier.fillMaxWidth()) { Text(if(editingInvoiceId==null) "Create bill and deduct stock" else "Save bill changes") }
       }
        "Payment" -> {
        val selectedPaymentInvoice = invoices.firstOrNull { it.id.toString() == paymentInvoiceId }
        Text("Payments & collections", style=MaterialTheme.typography.headlineSmall)
        Text("Record customer payments, track outstanding balances, and review every transaction.", style=MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
         Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
          Text("COLLECT PAYMENT", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.onPrimaryContainer)
          Text("Select an invoice", style=MaterialTheme.typography.titleMedium)
          OutlinedTextField(paymentInvoiceId, { paymentInvoiceId=it }, label={ Text("Invoice ID") }, placeholder={ Text("Choose an invoice below") }, singleLine=true, modifier=Modifier.fillMaxWidth())
          selectedPaymentInvoice?.let { inv ->
           Text("${inv.number} · ${inv.customerName}", style=MaterialTheme.typography.titleSmall)
           Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
            Card(Modifier.weight(1f), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(10.dp)) { Text("Total", style=MaterialTheme.typography.labelSmall); Text("₹${"%.2f".format(inv.totalPaise/100.0)}", style=MaterialTheme.typography.titleSmall) } }
            Card(Modifier.weight(1f), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(10.dp)) { Text("Due", style=MaterialTheme.typography.labelSmall); Text("₹${"%.2f".format(inv.duePaise/100.0)}", style=MaterialTheme.typography.titleSmall, color=if(inv.duePaise>0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }
           }
          }
          OutlinedTextField(paymentAmount, { paymentAmount=it }, label={ Text("Amount received (₹)") }, placeholder={ Text("e.g. 500.00") }, singleLine=true, modifier=Modifier.fillMaxWidth())
          Text("Payment method", style=MaterialTheme.typography.titleSmall)
          Row(horizontalArrangement=Arrangement.spacedBy(6.dp), modifier=Modifier.fillMaxWidth()) {
           listOf("Cash", "Online", "Other").forEach { method ->
            FilterChip(selected=paymentMethod==method, onClick={ paymentMethod=method }, label={ Text(method) }, modifier=Modifier.weight(1f))
           }
          }
          Button(onClick={ lifecycleScope.launch {
           val id=paymentInvoiceId.toLongOrNull(); val amount=paymentAmount.toDoubleOrNull()
           if(id==null || amount==null || amount<=0) toast("Enter a valid invoice ID and payment amount")
           else runCatching { BillingRepository(db).recordPayment(id,(amount*100).toLong(),paymentMethod.trim().ifBlank{"Cash"}) }
            .onSuccess { toast("Payment recorded successfully"); paymentAmount="" }.onFailure { toast(it.message ?: "Payment failed") }
          } }, enabled=paymentInvoiceId.toLongOrNull()!=null && (paymentAmount.toDoubleOrNull() ?: 0.0)>0, modifier=Modifier.fillMaxWidth()) { Text("✓  Record payment") }
         }
        }
        Spacer(Modifier.height(16.dp))
        Text("Choose an invoice", style=MaterialTheme.typography.titleLarge)
        invoices.forEach { inv ->
         Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable { paymentInvoiceId=inv.id.toString(); paymentAmount="" }) {
          Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement=Arrangement.spacedBy(10.dp), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
           Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color=if(inv.duePaise<=0) androidx.compose.ui.graphics.Color(0xFFE0F4E8) else androidx.compose.ui.graphics.Color(0xFFFFE8D6), modifier=Modifier.size(44.dp)) {
            Box(contentAlignment=androidx.compose.ui.Alignment.Center) { Text(if(inv.duePaise<=0) "✓" else "₹", style=MaterialTheme.typography.titleMedium, color=if(inv.duePaise<=0) androidx.compose.ui.graphics.Color(0xFF176B3A) else androidx.compose.ui.graphics.Color(0xFF9A4D00)) }
           }
           Column(Modifier.weight(1f)) {
            Text("${inv.number} · ${inv.customerName}", style=MaterialTheme.typography.titleSmall)
            Text(if(inv.duePaise<=0) "PAID" else if(inv.paidPaise>0) "PARTIALLY PAID" else "PENDING", style=MaterialTheme.typography.labelSmall, color=if(inv.duePaise<=0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
           }
           Column(horizontalAlignment=androidx.compose.ui.Alignment.End) {
            Text("Due", style=MaterialTheme.typography.labelSmall)
            Text("₹${"%.2f".format(inv.duePaise/100.0)}", style=MaterialTheme.typography.titleSmall)
           }
          }
         }
        }
        Spacer(Modifier.height(12.dp))
        Text("Transaction history", style=MaterialTheme.typography.titleLarge)
        if(paymentHistory.isEmpty()) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("No payments recorded yet", style=MaterialTheme.typography.titleSmall); Text("Select an invoice and record a payment to see its transactions here.", style=MaterialTheme.typography.bodySmall) } }
        paymentHistory.forEach { payment ->
         Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) {
          Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement=Arrangement.spacedBy(10.dp), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
           Surface(shape=androidx.compose.foundation.shape.CircleShape, color=MaterialTheme.colorScheme.secondaryContainer, modifier=Modifier.size(40.dp)) { Box(contentAlignment=androidx.compose.ui.Alignment.Center) { Text("✓", style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.primary) } }
           Column(Modifier.weight(1f)) {
            Text(payment.method, style=MaterialTheme.typography.titleSmall)
            Text(java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(payment.paidAt)), style=MaterialTheme.typography.bodySmall)
           }
           Text("+₹${"%.2f".format(payment.amountPaise/100.0)}", style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.primary)
          }
         }
        }
       }
       "Prescription" -> {
        Text("Eye prescription", style=MaterialTheme.typography.titleLarge)
        Text("Choose a customer ID from the customer list. Enter values exactly as prescribed by the optometrist.", style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(prescriptionCustomerId, { prescriptionCustomerId=it }, label={ Text("Customer ID *") }, modifier=Modifier.fillMaxWidth())
        customers.forEach { customer ->
         TextButton(onClick={ prescriptionCustomerId=customer.id.toString() }) { Text("Select #${customer.id} · ${customer.name} · ${customer.phone}") }
        }
        Text("Power signs: + means plus power; − means minus power. Example: +1.25 or -2.00. Copy values exactly from the optometrist's prescription.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.secondary)
        Text("Right eye (OD)", style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(rightSphere, { rightSphere=it }, label={ Text("SPH / Sphere (+ or −)") }, placeholder={ Text("e.g. -2.00 or +1.25") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(rightCylinder, { rightCylinder=it }, label={ Text("CYL / Cylinder (+ or −)") }, placeholder={ Text("e.g. -0.75 or +0.75") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(rightAxis, { rightAxis=it }, label={ Text("Axis (0–180°)") }, placeholder={ Text("e.g. 90") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(rightAdd, { rightAdd=it }, label={ Text("ADD / Near addition (+ or −)") }, placeholder={ Text("e.g. +1.50") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
        Text("Left eye (OS)", style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(leftSphere, { leftSphere=it }, label={ Text("SPH / Sphere (+ or −)") }, placeholder={ Text("e.g. -1.50 or +1.00") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(leftCylinder, { leftCylinder=it }, label={ Text("CYL / Cylinder (+ or −)") }, placeholder={ Text("e.g. -0.50 or +0.50") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(leftAxis, { leftAxis=it }, label={ Text("Axis (0–180°)") }, placeholder={ Text("e.g. 180") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(leftAdd, { leftAdd=it }, label={ Text("ADD / Near addition (+ or −)") }, placeholder={ Text("e.g. +1.50") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(pd, { pd=it }, label={ Text("Pupillary distance (PD, mm)") }, modifier=Modifier.fillMaxWidth())
        OutlinedTextField(prescriptionNotes, { prescriptionNotes=it }, label={ Text("Notes / lens recommendation") }, modifier=Modifier.fillMaxWidth(), minLines=2)
        Button(onClick={ if(!canManageRecords()) { denyPermission("manage prescriptions") } else lifecycleScope.launch {
         val cid=prescriptionCustomerId.toLongOrNull()
         val rightAxisValue=rightAxis.toIntOrNull()
         val leftAxisValue=leftAxis.toIntOrNull()
         if(cid==null || customers.none{it.id==cid}) toast("Select an existing customer")
         else if((rightAxis.isNotBlank() && (rightAxisValue==null || rightAxisValue !in 0..180)) || (leftAxis.isNotBlank() && (leftAxisValue==null || leftAxisValue !in 0..180))) toast("Axis must be a number from 0 to 180")
         else {
          val rx = Prescription(id=editingPrescriptionId ?: 0L, customerId=cid,rightSphere=rightSphere.trim(),rightCylinder=rightCylinder.trim(),rightAxis=rightAxis.trim(),rightAdd=rightAdd.trim(),leftSphere=leftSphere.trim(),leftCylinder=leftCylinder.trim(),leftAxis=leftAxis.trim(),leftAdd=leftAdd.trim(),pd=pd.trim(),notes=prescriptionNotes.trim())
          if(editingPrescriptionId == null) db.dao().addPrescription(rx) else db.dao().updatePrescription(rx)
          editingPrescriptionId=null
          rightSphere="";rightCylinder="";rightAxis="";rightAdd="";leftSphere="";leftCylinder="";leftAxis="";leftAdd="";pd="";prescriptionNotes=""
          toast("Prescription saved")
         }
        } }, modifier=Modifier.fillMaxWidth()) { Text(if(editingPrescriptionId == null) "Save prescription" else "Update prescription") }
        if(editingPrescriptionId != null) TextButton(onClick={ editingPrescriptionId=null; rightSphere="";rightCylinder="";rightAxis="";rightAdd="";leftSphere="";leftCylinder="";leftAxis="";leftAdd="";pd="";prescriptionNotes="" }) { Text("Cancel prescription edit") }
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
           Row {
            if(canManageRecords()) TextButton(onClick={ editingPrescriptionId=rx.id; prescriptionCustomerId=rx.customerId.toString(); rightSphere=rx.rightSphere;rightCylinder=rx.rightCylinder;rightAxis=rx.rightAxis;rightAdd=rx.rightAdd;leftSphere=rx.leftSphere;leftCylinder=rx.leftCylinder;leftAxis=rx.leftAxis;leftAdd=rx.leftAdd;pd=rx.pd;prescriptionNotes=rx.notes }) { Text("Edit") }
            val prescriptionCustomerName = customers.firstOrNull { it.id==rx.customerId }?.name ?: "Customer #${rx.customerId}"
            TextButton(onClick={ sharePrescriptionPdf(rx, prescriptionCustomerName) }) { Text("PDF / Share") }
            TextButton(onClick={ printPrescriptionPdf(rx, prescriptionCustomerName) }) { Text("Print") }
            if(canDeleteRecords()) TextButton(onClick={ if(!canDeleteRecords()) denyPermission("delete prescriptions") else lifecycleScope.launch { runCatching { db.dao().deletePrescription(rx) }.onSuccess { if(editingPrescriptionId==rx.id) editingPrescriptionId=null; toast("Prescription deleted") }.onFailure { toast("Could not delete prescription") } } }) { Text("Delete") }
           }
          }
         }
        }
       }
       "Reports" -> {
        Text("Shop reports", style=MaterialTheme.typography.titleLarge)
        val fromMillis = remember(reportFrom) { if (reportFrom.isBlank()) null else parseReportDate(reportFrom) }
        val toMillisExclusive = remember(reportTo) {
         if (reportTo.isBlank()) null else parseReportDate(reportTo)?.let { start ->
          Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
         }
        }
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
        val profitFrom = fromMillis ?: Long.MIN_VALUE
        val profitTo = toMillisExclusive ?: Long.MAX_VALUE
        val estimatedProfit by remember(profitFrom, profitTo) { db.dao().estimatedProfit(profitFrom, profitTo) }.collectAsState(initial = 0L)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Invoices"); Text("${reportInvoices.size}", style=MaterialTheme.typography.headlineSmall) } }
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Sales"); Text("₹${"%.2f".format(totalSales/100.0)}", style=MaterialTheme.typography.titleMedium) } }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Collected"); Text("₹${"%.2f".format(totalPaid/100.0)}", style=MaterialTheme.typography.titleMedium) } }
         Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) { Text("Outstanding"); Text("₹${"%.2f".format(totalDue/100.0)}", style=MaterialTheme.typography.titleMedium) } }
        }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("Estimated gross profit (excluding tax)"); Text("₹${"%.2f".format(estimatedProfit/100.0)}", style=MaterialTheme.typography.titleLarge); Text("Uses purchase cost saved at invoice time. Older invoices migrated from v1 have zero recorded cost and cannot show historical profit accurately.", style=MaterialTheme.typography.bodySmall) } }
        Text("Current inventory", style=MaterialTheme.typography.titleMedium)
        val stockUnits = products.sumOf { it.stock.toLong() }
        val inventoryCost = products.sumOf { it.stock.toLong() * it.costPaise }
        val inventoryRetail = products.sumOf { it.stock.toLong() * it.pricePaise }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
         Card(Modifier.weight(1f)) { Column(Modifier.padding(10.dp)) { Text("Stock units"); Text("$stockUnits", style=MaterialTheme.typography.titleMedium) } }
         Card(Modifier.weight(1f)) { Column(Modifier.padding(10.dp)) { Text("Cost value"); Text("₹${"%.2f".format(inventoryCost/100.0)}", style=MaterialTheme.typography.titleMedium) } }
        }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) { Text("Retail value at current prices"); Text("₹${"%.2f".format(inventoryRetail/100.0)}", style=MaterialTheme.typography.titleMedium) } }
        Text("Stock by category", style=MaterialTheme.typography.titleSmall)
        products.groupBy { it.category.trim().ifBlank { "Uncategorised" } }.toSortedMap().forEach { (category, entries) ->
         val units = entries.sumOf { it.stock.toLong() }
         val costValue = entries.sumOf { it.stock.toLong() * it.costPaise }
         Text("$category · ${entries.size} products · $units units · cost ₹${"%.2f".format(costValue/100.0)}", style=MaterialTheme.typography.bodySmall)
        }
        products.sortedBy { it.stock }.forEach { product ->
         Text("${if(product.stock<=product.lowStock) "⚠ " else ""}${product.name} · stock ${product.stock} · cost ₹${"%.2f".format(product.costPaise/100.0)} · sell ₹${"%.2f".format(product.pricePaise/100.0)}", style=MaterialTheme.typography.bodySmall)
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
        Text("Profit is an estimate: invoice tax is excluded and recorded item cost is deducted. It is not net profit and excludes rent, wages and other operating expenses. Dates use the device local time zone.", style=MaterialTheme.typography.bodySmall)
       }
       "Users" -> {
        Text("User management", style=MaterialTheme.typography.headlineSmall)
        Text("Create separate local accounts for shop staff. Only the owner can manage accounts.", style=MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(newStaffName,{newStaffName=it},label={Text("Staff username")},singleLine=true,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(newStaffPassword,{newStaffPassword=it},label={Text("Temporary password (8+ characters)")},singleLine=true,modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
         FilterChip(selected=newStaffRole=="Staff",onClick={newStaffRole="Staff"},label={Text("Staff")})
         FilterChip(selected=newStaffRole=="Manager",onClick={newStaffRole="Manager"},label={Text("Manager")})
        }
        Button(onClick={
         if(!isOwner()) denyPermission("manage user accounts") else runCatching {
          accountStore.create(newStaffName.trim(),newStaffPassword,newStaffRole)
          accountVersion++
          newStaffName="";newStaffPassword=""
         }.onSuccess { toast("User account created") }.onFailure { toast(it.message ?: "Could not create user") }
        },modifier=Modifier.fillMaxWidth(),enabled=newStaffName.trim().length>=3&&newStaffPassword.length>=8) { Text("Create staff account") }
        Spacer(Modifier.height(12.dp))
        Text("Accounts on this device",style=MaterialTheme.typography.titleMedium)
        accountVersion
        accountStore.loadUsers().forEach { user ->
         Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
          Column(Modifier.weight(1f)) { Text(user.username,style=MaterialTheme.typography.titleSmall); Text(user.role,style=MaterialTheme.typography.bodySmall) }
          if(isOwner() && user.role!="Owner" && user.username!=loggedInUser?.username) TextButton(onClick={ if(isOwner()) { accountStore.remove(user.username);accountVersion++;toast("Account removed") } else denyPermission("manage user accounts") }) { Text("Remove") }
         } }
        }
        Text("This is device-local account management, not cloud identity or multi-device synchronization.",style=MaterialTheme.typography.bodySmall)
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
        Divider()
        if(isOwner()) {
        Text("Optional app PIN lock", style=MaterialTheme.typography.titleMedium)
        Text("Set a 4–8 digit local PIN to require unlocking when the app opens. If you forget it, clear app data to remove it; there is no online recovery.", style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(newPin, { newPin=it.filter(Char::isDigit).take(8) }, label={Text("New PIN (4–8 digits)")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(confirmPin, { confirmPin=it.filter(Char::isDigit).take(8) }, label={Text("Confirm PIN")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
        Button(onClick={
         if(newPin.length !in 4..8 || newPin != confirmPin) toast("PIN must be 4–8 digits and both entries must match")
         else runCatching {
          val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
          val encodedSalt=java.util.Base64.getEncoder().encodeToString(salt)
          val encodedHash=java.util.Base64.getEncoder().encodeToString(derivePinHash(newPin,salt))
          securityPrefs.edit().putString("pin_salt",encodedSalt).putString("pin_hash",encodedHash).apply()
          newPin="";confirmPin=""
         }.onSuccess { toast("App PIN saved. It will be required next time the app opens.") }.onFailure { toast("Could not save PIN") }
        }, modifier=Modifier.fillMaxWidth()) { Text("Save / change app PIN") }
        OutlinedButton(onClick={ securityPrefs.edit().remove("pin_hash").remove("pin_salt").apply(); newPin="";confirmPin="";toast("App PIN disabled") }, modifier=Modifier.fillMaxWidth()) { Text("Disable app PIN") }
        Divider()
        Text("Database backup and restore", style=MaterialTheme.typography.titleMedium)
        Text("Export a full local database backup and keep it private. Restore replaces the current local database; only restore a backup made by this app.", style=MaterialTheme.typography.bodySmall)
        if(isOwner()) Button(onClick={ backupLauncher.launch("surya-optician-backup.db") }, modifier=Modifier.fillMaxWidth()) { Text("Export database backup") }
        if(isOwner()) OutlinedButton(onClick={ restoreLauncher.launch(arrayOf("application/vnd.sqlite3", "application/octet-stream", "application/x-sqlite3", "*/*")) }, modifier=Modifier.fillMaxWidth()) { Text("Restore database backup") }
        }
       }
       else -> {
        OutlinedTextField(query, { query=it }, label={Text("Search invoice, customer, phone")}, modifier=Modifier.fillMaxWidth())
        Button(onClick={ InvoiceCsv.share(this@MainActivity, invoices) }, modifier=Modifier.fillMaxWidth()) { Text("Export visible invoices as CSV") }
        Column {
         invoices.forEach { invoice ->
          Card(Modifier.fillMaxWidth().padding(vertical=5.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
           Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
             Column(Modifier.weight(1f)) {
              Text(invoice.number, style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.secondary)
              Text(invoice.customerName, style=MaterialTheme.typography.titleMedium)
             }
             Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(50), color=if(invoice.duePaise<=0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer) {
              Text(if(invoice.duePaise<=0) "PAID" else if(invoice.paidPaise>0) "PART PAID" else "UNPAID", modifier=Modifier.padding(horizontal=10.dp, vertical=6.dp), style=MaterialTheme.typography.labelSmall, color=if(invoice.duePaise<=0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.error)
             }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
             Card(Modifier.weight(1f), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(10.dp)) { Text("TOTAL", style=MaterialTheme.typography.labelSmall); Text("₹${"%.2f".format(invoice.totalPaise/100.0)}", style=MaterialTheme.typography.titleSmall) } }
             Card(Modifier.weight(1f), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(10.dp)) { Text("PAID", style=MaterialTheme.typography.labelSmall); Text("₹${"%.2f".format(invoice.paidPaise/100.0)}", style=MaterialTheme.typography.titleSmall) } }
             Card(Modifier.weight(1f), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(10.dp)) { Text("DUE", style=MaterialTheme.typography.labelSmall); Text("₹${"%.2f".format(invoice.duePaise/100.0)}", style=MaterialTheme.typography.titleSmall, color=if(invoice.duePaise>0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }
            }
            val paymentStatus = if (invoice.duePaise <= 0) "PAID / RECORDED" else if (invoice.paidPaise > 0) "PARTIALLY PAID · BALANCE DUE" else "UNPAID · PAYMENT DUE"
            Text("Payment status: $paymentStatus", color=if(invoice.duePaise<=0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style=MaterialTheme.typography.labelMedium)
            if(invoice.duePaise>0) TextButton(onClick={ paymentInvoiceId=invoice.id.toString(); paymentAmount=""; section="Payment" }) { Text("Collect due payment") }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
             if(canManageRecords()) TextButton(onClick={ lifecycleScope.launch { runCatching { val items=db.dao().items(invoice.id); billCustomer=invoice.customerName; billCustomerId=invoice.customerId?.toString() ?: ""; billPhone=invoice.phone; billLines=items.map { BillLine(it.productId,it.description,it.qty,it.ratePaise,it.discountPaise,it.taxPaise) }; billPaid="%.2f".format(invoice.paidPaise/100.0); billPrescriptionId=""; billPrescriptionSummary=invoice.prescriptionSummary; billGlassType=invoice.glassType; billDeliveryDate=invoice.deliveryDate; editingInvoiceId=invoice.id; section="New Bill" }.onFailure { toast("Could not load bill for editing") } } }) { Text("Edit") }
             if(canDeleteRecords()) TextButton(onClick={ invoiceDeleteTarget=invoice }) { Text("Delete") }
             TextButton(onClick={lifecycleScope.launch{runCatching{val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings());val uri=FileProvider.getUriForFile(this@MainActivity,"${packageName}.fileprovider",file);startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{toast("PDF preview failed. Install a PDF viewer.")}}}){Text("Preview PDF")}
             TextButton(onClick={lifecycleScope.launch{runCatching{val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings());printPdf(file,"Invoice ${invoice.number}")}.onFailure{toast("Could not start system print")}}}){Text("Print")}
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
    }
    invoiceDeleteTarget?.let { target ->
     AlertDialog(
      onDismissRequest={ invoiceDeleteTarget=null },
      title={ Text("Delete bill ${target.number}?") },
      text={ Text("This permanently deletes this invoice, its item and payment records, and restores stock for inventory products. This cannot be undone. Export a backup first if you need to retain the accounting record.") },
      confirmButton={ TextButton(onClick={
       val selected=target; invoiceDeleteTarget=null
       lifecycleScope.launch { runCatching { BillingRepository(db).deleteBill(selected.id) }
        .onSuccess { toast("Bill deleted and stock restored"); if(editingInvoiceId==selected.id) { editingInvoiceId=null; billLines=emptyList(); billPrescriptionSummary="" } }
        .onFailure { toast(it.message ?: "Could not delete bill") }
       }
      }) { Text("Delete permanently", color=MaterialTheme.colorScheme.error) } },
      dismissButton={ TextButton(onClick={invoiceDeleteTarget=null}) { Text("Cancel") } }
     )
    }
    if (showBillPreview) {
     val previewTotal = billLines.sumOf { it.total }
     AlertDialog(
      onDismissRequest = { showBillPreview = false },
      title = { Text("Bill preview") },
      text = {
       Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState())) {
        Text("SURYA OPTICIAN", style=MaterialTheme.typography.titleMedium)
        Text("Customer: " + billCustomer.ifBlank { customers.firstOrNull { it.id.toString()==billCustomerId }?.name ?: "Not selected" })
        Text("Phone: " + billPhone.ifBlank { customers.firstOrNull { it.id.toString()==billCustomerId }?.phone ?: "—" })
        if (billGlassType.isNotBlank()) Text("Glass type: $billGlassType")
        if (billDeliveryDate.isNotBlank()) Text("Expected delivery: $billDeliveryDate")
        Spacer(Modifier.height(8.dp))
        billLines.forEachIndexed { index, line ->
         Text("${index+1}. ${line.description} × ${line.qty} — ₹${"%.2f".format(line.total/100.0)}")
        }
        Divider(Modifier.padding(vertical=8.dp))
        Text("Subtotal: ₹${"%.2f".format(billLines.sumOf { it.subtotalPaise }/100.0)}")
        Text("Discount: ₹${"%.2f".format(billLines.sumOf { it.discountPaise }/100.0)}")
        Text("Tax: ₹${"%.2f".format(billLines.sumOf { it.taxPaise }/100.0)}")
        Text("GRAND TOTAL: ₹${"%.2f".format(previewTotal/100.0)}", style=MaterialTheme.typography.titleMedium)
        Text("Paid: ₹${"%.2f".format(((billPaid.toDoubleOrNull() ?: 0.0)*100).toLong()/100.0)}")
        Text("Balance: ₹${"%.2f".format((previewTotal-((billPaid.toDoubleOrNull() ?: 0.0)*100).toLong()).coerceAtLeast(0)/100.0)}")
        selectedBillPrescription?.let { rx ->
         Spacer(Modifier.height(8.dp))
         Text("EYE PRESCRIPTION", style=MaterialTheme.typography.titleSmall)
         Text("OD: SPH ${rx.rightSphere.ifBlank{"—"}} · CYL ${rx.rightCylinder.ifBlank{"—"}} · Axis ${rx.rightAxis.ifBlank{"—"}} · ADD ${rx.rightAdd.ifBlank{"—"}}")
         Text("OS: SPH ${rx.leftSphere.ifBlank{"—"}} · CYL ${rx.leftCylinder.ifBlank{"—"}} · Axis ${rx.leftAxis.ifBlank{"—"}} · ADD ${rx.leftAdd.ifBlank{"—"}}")
         Text("PD: ${rx.pd.ifBlank{"—"}}")
        }
       }
      },
      confirmButton = { TextButton(onClick={ showBillPreview=false }) { Text("Back to edit") } },
      dismissButton = { TextButton(onClick={ showBillPreview=false }) { Text("Close preview") } }
     )
    }
    if (loggedInUser != null && !unlocked) {
     AlertDialog(
      onDismissRequest = {},
      title = { Text("Unlock Surya Optician") },
      text = {
       Column {
        Text("Enter your app PIN to access shop records.")
        OutlinedTextField(unlockPin, { unlockPin=it.filter(Char::isDigit).take(8); pinError="" }, label={Text("PIN")}, singleLine=true, modifier=Modifier.fillMaxWidth())
        if(pinError.isNotBlank()) Text(pinError, color=MaterialTheme.colorScheme.error)
       }
      },
      confirmButton = {
       TextButton(onClick={
        runCatching {
         val salt=securityPrefs.getString("pin_salt",null)?.let { java.util.Base64.getDecoder().decode(it) } ?: error("PIN is not configured correctly")
         val expected=securityPrefs.getString("pin_hash",null)?.let { java.util.Base64.getDecoder().decode(it) } ?: error("PIN is not configured correctly")
         check(MessageDigest.isEqual(derivePinHash(unlockPin,salt),expected)) { "Incorrect PIN" }
         unlocked=true;unlockPin="";pinError=""
        }.onFailure { pinError=it.message ?: "Unable to unlock" }
       }) { Text("Unlock") }
      }
     )
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
