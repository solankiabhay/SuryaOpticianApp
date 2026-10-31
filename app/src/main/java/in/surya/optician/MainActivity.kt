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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import `in`.surya.optician.data.*
import `in`.surya.optician.ui.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Visibility
import `in`.surya.optician.invoice.InvoicePdf
import `in`.surya.optician.invoice.InvoiceCsv
import `in`.surya.optician.invoice.InvoiceSharing
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import java.net.URLEncoder
import java.text.ParsePosition
import java.util.Calendar


@Composable
private fun OpticianLogo(
 modifier: Modifier = Modifier,
 color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF123A63)
) {
 SuryaLogo(modifier=modifier, eyeColor=color)
}

@Composable
private fun ReportBarChart(values: List<Pair<String, Long>>, modifier: Modifier = Modifier) {
 val barColor = MaterialTheme.colorScheme.primary
 val maxValue = (values.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
 Column(modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Row(Modifier.fillMaxWidth().height(156.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Bottom) {
   values.forEach { (label,amount) ->
    val fraction=(amount.toDouble()/maxValue.toDouble()).coerceIn(0.0,1.0)
    Column(Modifier.weight(1f).fillMaxHeight(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
     Text(formatRupees(amount),style=MaterialTheme.typography.labelSmall,maxLines=1,color=MaterialTheme.colorScheme.onSurfaceVariant)
     Spacer(Modifier.height(5.dp))
     Box(Modifier.widthIn(min=12.dp,max=28.dp).fillMaxWidth().height((112.0*fraction).coerceAtLeast(5.0).dp).background(barColor,RoundedCornerShape(topStart=7.dp,topEnd=7.dp)))
     Spacer(Modifier.height(5.dp))
     Text(label,style=MaterialTheme.typography.labelSmall,maxLines=1,color=MaterialTheme.colorScheme.onSurface)
    }
   }
  }
 }
}

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
   SuryaTheme {
    val accountStore = remember { UserAccounts(this@MainActivity) }
    var accountVersion by remember { mutableIntStateOf(0) }
    var loggedInUser by remember { mutableStateOf<LocalUser?>(null) }
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
        check(sqlite.version in 1..8) { "Unsupported database backup version" }
        sqlite.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('customers','products','invoices','invoice_items','payments','prescriptions','shop_settings')", null).use { cursor ->
         check(cursor.moveToFirst() && cursor.getInt(0) == 7) { "Selected file is not a complete Surya Optician database backup" }
        }
       }
       val databaseFile = getDatabasePath("surya-optician.db")
       val stagedFile = File(databaseFile.parentFile, "surya-optician-restore-staged.db")
       val recoveryFile = File(cacheDir, "surya-optician-before-restore.db")
       check(temp.copyTo(stagedFile, overwrite = true).exists()) { "Could not stage the restored database" }
       if (databaseFile.exists()) {
        check(databaseFile.copyTo(recoveryFile, overwrite = true).exists()) { "Could not create a recovery copy of the current database" }
       }
       OpticianDatabase.resetInstance()
       File(databaseFile.absolutePath + "-wal").delete()
       File(databaseFile.absolutePath + "-shm").delete()
       if (databaseFile.exists()) check(databaseFile.delete()) { "Could not replace the current database file" }
       if (!stagedFile.renameTo(databaseFile)) {
        if (recoveryFile.exists()) recoveryFile.copyTo(databaseFile, overwrite = true)
        error("Could not install the restored database; the previous database was retained when possible")
       }
      }.onSuccess { temp.delete(); Toast.makeText(this@MainActivity, "Backup restored. Reopening app.", Toast.LENGTH_LONG).show(); recreate() }.onFailure { Toast.makeText(this@MainActivity, "Restore failed: ${it.message ?: "unknown error"}. A recovery copy was retained in app cache.", Toast.LENGTH_LONG).show() }
     }
    }
    val securityPrefs = remember { getSharedPreferences("surya_security", MODE_PRIVATE) }

    var authName by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var authConfirmPassword by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf("") }
    var newStaffName by remember { mutableStateOf("") }
    var newStaffPassword by remember { mutableStateOf("") }
    var newStaffRole by remember { mutableStateOf("Staff") }
    var editingStaffUsername by remember { mutableStateOf<String?>(null) }
    var unlocked by remember { mutableStateOf(securityPrefs.getString("pin_hash", null) == null) }
    var unlockPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("Dashboard") }
    val mainScroll = rememberScrollState()
    LaunchedEffect(section) { mainScroll.scrollTo(0) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
     var legacyDownloadFile by remember { mutableStateOf<File?>(null) }
     var reportQuery by remember { mutableStateOf("") }
    var billHistoryPage by remember { mutableIntStateOf(0) }
    var historyFilter by remember { mutableStateOf("All") }
    var savingBill by remember { mutableStateOf(false) }
    var billTouched by remember { mutableStateOf(false) }
    var customerSearch by remember { mutableStateOf("") }
    var customerTouched by remember { mutableStateOf(false) }
    var productSearch by remember { mutableStateOf("") }
    var editingPrescriptionId by remember { mutableStateOf<Long?>(null) }
    var returnToBillAfterPrescription by remember { mutableStateOf(false) }
    var reportFrom by remember { mutableStateOf("") }
    var reportTo by remember { mutableStateOf("") }
    var reportAppliedFrom by remember { mutableStateOf("") }
    var reportAppliedTo by remember { mutableStateOf("") }
    var reportType by remember { mutableStateOf("Collection") }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedCustomerId) { if (selectedCustomerId != null) mainScroll.animateScrollTo(0) }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var customerEmail by remember { mutableStateOf("") }
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
    var billCustomerSearch by remember { mutableStateOf("") }
    var billItemSearch by remember { mutableStateOf("") }
    var billCustomerAddress by remember { mutableStateOf("") }
    var billCustomerAge by remember { mutableStateOf("") }
    var billCustomerEmail by remember { mutableStateOf("") }
    var billCustomerSearchMessage by remember { mutableStateOf("") }
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
    val voiceBillingLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
     androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
     if (result.resultCode == android.app.Activity.RESULT_OK) {
      val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
      val command = VoiceBillCommandParser.parse(spoken)
      var applied = false
      command.customerName?.let { billCustomer=it; billCustomerSearch=it; billCustomerId=""; applied=true }
      command.phone?.let { billPhone=it; applied=true }
      command.age?.let { billCustomerAge=it; applied=true }
      command.itemDescription?.let { description ->
       val amount = command.itemAmount?.let { Money.parseRupeesToPaise(it) }
       if (amount != null && amount >= 0L) {
        billLines = billLines + BillLine(null, description, 1, amount)
        billDescription=""; billRate=""
        applied=true
       }
      }
      command.advanceAmount?.let { billPaid=it; applied=true }
      command.paymentMethod?.let { billPaymentMethod=it; applied=true }
      val asksToSave = Regex("(?i)\\b(save|save it|save bill|bill save|bill ko save|save kar do|save karo)\\b").containsMatchIn(spoken)
      if (asksToSave) {
       showVoiceSaveConfirmation = true
       Toast.makeText(this@MainActivity, "Please confirm the bill details before saving.", Toast.LENGTH_LONG).show()
      } else {
       Toast.makeText(this@MainActivity, if(applied) "Voice details added. Please review the bill." else "Command not recognized. Try: customer name Rahul, add item frame 1500, advance 500 cash.", Toast.LENGTH_LONG).show()
      }
     }
    }
    var billPrescriptionId by remember { mutableStateOf("") }
    var billPrescriptionSummary by remember { mutableStateOf("") }
    var billEyeTestFee by remember { mutableStateOf("") }
    var savingCustomerForPrescription by remember { mutableStateOf(false) }
    var showPrescriptionCustomerForm by remember { mutableStateOf(false) }
    var billPrescriptionExpanded by remember { mutableStateOf(false) }
    var billGlassType by remember { mutableStateOf("") }
    var billDeliveryDate by remember { mutableStateOf("") }
    var showBillPreview by remember { mutableStateOf(false) }
    var showVoiceSaveConfirmation by remember { mutableStateOf(false) }
    var editingInvoiceId by remember { mutableStateOf<Long?>(null) }
    var invoiceDeleteTarget by remember { mutableStateOf<Invoice?>(null) }
    var paymentInvoiceId by remember { mutableStateOf("") }
    var paymentAmount by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Cash") }
    var paymentReference by remember { mutableStateOf("") }
    var editingPaymentId by remember { mutableStateOf<Long?>(null) }
    var paymentDeleteTarget by remember { mutableStateOf<Payment?>(null) }
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
    val searchedInvoices by db.dao().searchInvoices(query).collectAsState(initial = emptyList())
    val invoices = searchedInvoices.filter { when(historyFilter) { "Paid" -> it.duePaise <= 0; "Due" -> it.duePaise > 0; else -> true } }
    val legacyPdfDownloadLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { destination ->
     val source = legacyDownloadFile
     legacyDownloadFile = null
     if (destination != null && source != null) runCatching {
      contentResolver.openOutputStream(destination)?.use { output -> source.inputStream().use { input -> input.copyTo(output) } } ?: error("Could not open the selected download location")
     }.onSuccess { Toast.makeText(this@MainActivity, "Invoice PDF downloaded", Toast.LENGTH_LONG).show() }.onFailure { Toast.makeText(this@MainActivity, it.message ?: "Invoice download failed", Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(query, historyFilter) { billHistoryPage = 0 }
    val billHistoryPageSize = 10
    val filteredBillHistory = invoices.filter { invoice -> when (historyFilter) { "Paid" -> invoice.duePaise <= 0; "Due" -> invoice.duePaise > 0; else -> true } }
    val billHistoryPageCount = maxOf(1, (filteredBillHistory.size + billHistoryPageSize - 1) / billHistoryPageSize)
    val visibleBillHistory = filteredBillHistory.drop(billHistoryPage * billHistoryPageSize).take(billHistoryPageSize)
    val allInvoices by db.dao().invoices().collectAsState(initial = emptyList())
    val allPayments by db.dao().allPayments().collectAsState(initial = emptyList())
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
    val selectedBillCustomer = customers.firstOrNull { it.id.toString() == billCustomerId }
    val pendingItem = BillDraft.parsePending(billProductId, billDescription, billQty, billRate, billDiscount, billTax)
    val eyeTestFeePaise = Money.parseRupeesToPaise(billEyeTestFee)
    val hasSaleLines = billLines.isNotEmpty() || pendingItem is PendingItem.Ready
    val hasPrescription = billPrescriptionId.isNotBlank() || billPrescriptionSummary.isNotBlank()
    val eyeTestFeeLine = runCatching { PrescriptionBilling.chargeLine(billEyeTestFee, hasPrescription && !hasSaleLines) }.getOrNull()?.let(::listOf) ?: emptyList()
    val effectiveBillLines = billLines + listOfNotNull((pendingItem as? PendingItem.Ready)?.line) + eyeTestFeeLine
    val products by db.dao().products().collectAsState(initial = emptyList())
    val due by db.dao().outstanding().collectAsState(initial = 0L)
    val lowStock by db.dao().lowStock().collectAsState(initial = emptyList())

    fun toast(message: String) = Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
    val currentRole = loggedInUser?.role ?: "Staff"
    fun isOwner() = RolePermissions.isOwner(currentRole)
    fun canManageRecords() = RolePermissions.canManageRecords(currentRole)
    fun canDeleteRecords() = RolePermissions.canDeleteRecords(currentRole)
    fun canAccess(tab: String): Boolean = RolePermissions.canAccess(currentRole, tab)
    fun denyPermission(action: String) { toast("Permission denied: your role cannot $action.") }
    if (loggedInUser == null) {
     val needsOwnerSetup = accountStore.loadUsers().isEmpty()
     Box(
      modifier=Modifier.fillMaxSize().background(
       androidx.compose.ui.graphics.Brush.verticalGradient(
        colors=listOf(androidx.compose.ui.graphics.Color(0xFF0B2A4A),androidx.compose.ui.graphics.Color(0xFF123A63),androidx.compose.ui.graphics.Color(0xFFF4F7FB)),
        startY=0f,
        endY=1200f
       )
      )
     ) {
      Column(
       modifier=Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=22.dp, vertical=28.dp),
       horizontalAlignment=Alignment.CenterHorizontally,
       verticalArrangement=Arrangement.Center
      ) {
       Spacer(Modifier.height(14.dp))
       Surface(
        modifier=Modifier.size(96.dp),
        shape=androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
        color=androidx.compose.ui.graphics.Color.White,
        shadowElevation=12.dp
       ) {
        Box(contentAlignment=Alignment.Center) { SuryaLogo(size=70.dp, eyeColor=Brand.Navy) }
       }
       Spacer(Modifier.height(16.dp))
       Text("SURYA", style=MaterialTheme.typography.headlineMedium, color=androidx.compose.ui.graphics.Color.White, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing=3.sp)
       Text("OPTICIAN  •  SHOP MANAGEMENT", style=MaterialTheme.typography.labelMedium, color=androidx.compose.ui.graphics.Color(0xFFDCEBFA), letterSpacing=1.2.sp)
       Spacer(Modifier.height(28.dp))
       Card(
        modifier=Modifier.fillMaxWidth().widthIn(max=440.dp),
        shape=androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color.White),
        elevation=CardDefaults.cardElevation(defaultElevation=12.dp)
       ) {
        Column(
         modifier=Modifier.fillMaxWidth().padding(24.dp),
         verticalArrangement=Arrangement.spacedBy(12.dp)
        ) {
         Text(if(needsOwnerSetup) "Set up your shop" else "Welcome back", style=MaterialTheme.typography.headlineSmall, color=androidx.compose.ui.graphics.Color(0xFF102D4A), fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
         Text(
          if(needsOwnerSetup) "Create your owner account to keep your shop records protected." else "Sign in to continue to your billing workspace.",
          style=MaterialTheme.typography.bodyMedium,
          color=androidx.compose.ui.graphics.Color(0xFF647587)
         )
         Spacer(Modifier.height(4.dp))
         AppField(value=authName, onValueChange={ authName=it; authError="" }, label=if(needsOwnerSetup) "Owner username" else "Username", icon=Icons.Rounded.Person, isError=authError.startsWith("Username"))
         PasswordField(value=authPassword, onValueChange={ authPassword=it; authError="" }, label="Password", isError=authError.startsWith("Password") || authError.startsWith("Incorrect"), supportingText=if(needsOwnerSetup) "At least 8 characters" else null)
         if(needsOwnerSetup) PasswordField(value=authConfirmPassword, onValueChange={ authConfirmPassword=it; authError="" }, label="Confirm password", isError=authError.startsWith("Passwords"))
         if(authError.isNotBlank()) {
          Surface(
           color=androidx.compose.ui.graphics.Color(0xFFFFEEEE),
           shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
           modifier=Modifier.fillMaxWidth()
          ) { Text(authError, modifier=Modifier.padding(12.dp), color=MaterialTheme.colorScheme.error, style=MaterialTheme.typography.bodySmall) }
         }
         Spacer(Modifier.height(2.dp))
         PrimaryAction(text=if(needsOwnerSetup) "Create owner account" else "Sign in", onClick={
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
          }, icon=Icons.Rounded.Lock)
         Text(
          "Your account stays on this device. Keep regular backups and never share your password.",
          style=MaterialTheme.typography.bodySmall,
          color=androidx.compose.ui.graphics.Color(0xFF738294)
         )
        }
       }
       Spacer(Modifier.height(18.dp))
       Text("CLEAR VISION. SIMPLE BILLING.", style=MaterialTheme.typography.labelSmall, color=androidx.compose.ui.graphics.Color(0xFFDCEBFA), letterSpacing=1.1.sp)
      }
     }
    } else {
    Scaffold(
     containerColor = MaterialTheme.colorScheme.background,
     topBar = {
      TopAppBar(
       colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand.Navy, titleContentColor = androidx.compose.ui.graphics.Color.White, actionIconContentColor = androidx.compose.ui.graphics.Color.White),
       title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
         Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color=androidx.compose.ui.graphics.Color.White, modifier=Modifier.size(36.dp)) {
          Box(contentAlignment=Alignment.Center) { SuryaLogo(size=28.dp, eyeColor=Brand.Navy) }
         }
         Column {
          Text(when(section) { "Dashboard" -> "SURYA OPTICIAN"; "Bills" -> "Bill History"; "New Bill" -> if(editingInvoiceId==null) "New Bill" else "Edit Bill"; "Payment" -> "Payments"; else -> section }, style=MaterialTheme.typography.titleMedium, color=androidx.compose.ui.graphics.Color.White, maxLines=1)
          Text(if(section=="Dashboard") "Clear Vision · Better Life" else "Surya Optician", style=MaterialTheme.typography.labelSmall, color=androidx.compose.ui.graphics.Color(0xFFBDD8F4))
         }
        }
       },
       actions = {
        if (section == "New Bill") {
         IconButton(onClick={
          if (effectiveBillLines.isNotEmpty()) showBillPreview=true
          else toast("Add at least one item to preview the bill.")
         }) { Icon(Icons.Rounded.Visibility, contentDescription="Preview bill") }
        }
        IconButton(onClick={ loggedInUser=null;authName="";authPassword="";section="Dashboard" }) { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription="Sign out") }
       }
      )
     },
     bottomBar = {
      Box(Modifier.fillMaxWidth()) {
      NavigationBar(
       containerColor=MaterialTheme.colorScheme.surface,
       tonalElevation=8.dp,
       modifier=Modifier.fillMaxWidth()
      ) {
       val primaryTabs = listOf("Dashboard", "New Bill", "Bills", "Customers")
       val labels = listOf("Home", "New Bill", "History", "Clients")
       primaryTabs.forEachIndexed { index, tab ->
        NavigationBarItem(
         selected=section==tab,
         onClick={ section=tab; showMoreMenu=false },
         icon={ Icon(sectionIcon(tab), contentDescription=labels[index]) },
         label={ Text(labels[index], style=MaterialTheme.typography.labelSmall, maxLines=1) },
         alwaysShowLabel=true
        )
       }
       NavigationBarItem(
        selected=showMoreMenu || section !in primaryTabs,
        onClick={ showMoreMenu=true },
        icon={ Icon(Icons.Rounded.Menu, contentDescription="Menu") },
        label={ Text("More", style=MaterialTheme.typography.labelSmall, maxLines=1) },
        alwaysShowLabel=true
       )

      }
       if (showMoreMenu) {
        ModalBottomSheet(
         onDismissRequest={ showMoreMenu=false },
         containerColor=MaterialTheme.colorScheme.surface,
         dragHandle={ BottomSheetDefaults.DragHandle() }
        ) {
         Column(
          modifier=Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=16.dp).padding(bottom=20.dp),
          verticalArrangement=Arrangement.spacedBy(6.dp)
         ) {
          Text("Shop menu", style=MaterialTheme.typography.titleLarge)
          Text("Open a section", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
          val menuTabs = listOf("Dashboard", "Bills", "New Bill", "Customers", "Products", "Payment", "Prescription", "Reports", "Settings") + if(isOwner()) listOf("Users") else emptyList()
          menuTabs.distinct().filter { canAccess(it) }.forEach { tab ->
           Surface(
            modifier=Modifier.fillMaxWidth().clickable { section=tab; showMoreMenu=false },
            shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            color=if(section==tab) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
           ) {
            Row(
             modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical=14.dp),
             horizontalArrangement=Arrangement.spacedBy(14.dp),
             verticalAlignment=Alignment.CenterVertically
            ) {
             Icon(sectionIcon(tab), contentDescription=null, tint=MaterialTheme.colorScheme.primary)
             Text(if(tab=="Bills") "Bill History" else if(tab=="New Bill") "Create New Bill" else tab, style=MaterialTheme.typography.titleMedium)
             Spacer(Modifier.weight(1f))
             if(section==tab) Text("Current", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary)
            }
           }
          }
         }
        }
       }
     }
    }
    ) { padding ->
     Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=16.dp, vertical=12.dp))
     {
      Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(mainScroll)) {
      when (section) {
       "Dashboard" -> {
        val collected = allInvoices.sumOf { it.paidPaise }
        Card(Modifier.fillMaxWidth(), shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp), colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color.Transparent)) {
         Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF0E2F52), androidx.compose.ui.graphics.Color(0xFF1D5A8E))))) {
          Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
           Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
             Text(java.text.SimpleDateFormat("EEE, dd MMM", java.util.Locale.getDefault()).format(java.util.Date()).uppercase(), style=MaterialTheme.typography.labelMedium, color=androidx.compose.ui.graphics.Color(0xFFBDD8F4))
             Text("Namaste, ${loggedInUser?.username ?: "there"}", style=MaterialTheme.typography.titleLarge, color=androidx.compose.ui.graphics.Color.White, maxLines=1)
            }
            if(lowStock.isNotEmpty()) StatusPill("${lowStock.size} low stock", Brand.WarningBg, Brand.Warning, Modifier.clickable { section="Products" })
           }
           Button(
            onClick={ section="New Bill" },
            modifier=Modifier.fillMaxWidth().height(50.dp),
            shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            colors=ButtonDefaults.buttonColors(containerColor=androidx.compose.ui.graphics.Color.White, contentColor=Brand.Navy)
           ) { Icon(Icons.Rounded.Add, contentDescription=null); Spacer(Modifier.width(8.dp)); Text("Create New Bill", style=MaterialTheme.typography.titleMedium) }
          }
         }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
         MiniStat("Total bills", "${allInvoices.size}", Icons.Rounded.Receipt, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f), onClick={ section="Bills" })
         MiniStat("Collected", formatRupees(collected), Icons.Rounded.CheckCircle, Brand.Success, Brand.SuccessBg, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
         MiniStat("Outstanding", formatRupees(due), Icons.Rounded.Payments, Brand.Warning, Brand.WarningBg, Modifier.weight(1f), onClick={ section="Payment" })
         MiniStat("Customers", "${customers.size}", Icons.Rounded.People, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f), onClick={ section="Customers" })
        }
        Spacer(Modifier.height(16.dp))
        Text("Shortcuts", style=MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val shortcutTabs = listOf("Bills" to "Bill history", "Customers" to "Customers", "Products" to "Products & stock", "Payment" to "Collect payment", "Prescription" to "Prescriptions", "Reports" to "Reports", "Settings" to "Settings").filter { canAccess(it.first) }
        Card(Modifier.fillMaxWidth(), shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface), border=BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
         Column {
          shortcutTabs.forEachIndexed { i, entry ->
           MenuRow(
            icon=sectionIcon(entry.first),
            title=entry.second,
            onClick={ section=entry.first },
            badge=when(entry.first) { "Payment" -> if(due>0) formatRupees(due) else null; "Products" -> if(lowStock.isNotEmpty()) "${lowStock.size} low" else null; else -> null }
           )
           if(i<shortcutTabs.lastIndex) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant, modifier=Modifier.padding(start=50.dp))
          }
         }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
         Text("Recent bills", style=MaterialTheme.typography.titleMedium)
         TextButton(onClick={ section="Bills" }) { Text("View all") }
        }
        if(allInvoices.isEmpty()) EmptyState(Icons.Rounded.Receipt, "No bills yet", "Create your first bill to start tracking sales and payments.")
        else Card(Modifier.fillMaxWidth(), shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface), border=BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
         Column {
          allInvoices.take(4).forEachIndexed { i, invoice ->
           Row(Modifier.fillMaxWidth().clickable { section="Bills" }.padding(horizontal=14.dp, vertical=11.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
             Text(invoice.customerName, style=MaterialTheme.typography.titleSmall, maxLines=1)
             Text(invoice.number + "  ·  " + java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(invoice.createdAt)), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant, maxLines=1)
            }
            Column(horizontalAlignment=Alignment.End, verticalArrangement=Arrangement.spacedBy(4.dp)) {
             Text(formatRupees(invoice.totalPaise), style=MaterialTheme.typography.titleSmall)
             PaymentBadge(invoice.duePaise, invoice.paidPaise)
            }
           }
           if(i<minOf(allInvoices.size,4)-1) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
          }
         }
        }
        Spacer(Modifier.height(24.dp))
       }
       "Customers" -> {
        ScreenHeader(if(selectedCustomerId==null) "Customers" else "Edit customer", "Contact details and age for better service")
        Spacer(Modifier.height(12.dp))
        SectionCard(title=if(selectedCustomerId==null) "Add a customer" else "Update customer details", icon=Icons.Rounded.PersonAdd) {
         AppField(value=customerName, onValueChange={ customerName=it }, label="Customer name *", icon=Icons.Rounded.Person, isError=customerTouched && customerName.isBlank(), supportingText=if(customerTouched && customerName.isBlank()) "Enter the customer name" else null)
         Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
          AppField(value=customerPhone, onValueChange={ customerPhone=it }, label="Phone (WhatsApp)", modifier=Modifier.weight(1.5f), icon=Icons.Rounded.Phone, keyboardType=KeyboardType.Phone, isError=customerTouched && Validation.phoneError(customerPhone)!=null, supportingText=if(customerTouched) Validation.phoneError(customerPhone) else null)
          AppField(value=customerAge, onValueChange={ customerAge=it.filter(Char::isDigit).take(3) }, label="Age", modifier=Modifier.weight(1f), keyboardType=KeyboardType.Number, isError=customerTouched && Validation.ageError(customerAge)!=null, supportingText=if(customerTouched) Validation.ageError(customerAge) else null)
         }
         AppField(value=customerAddress, onValueChange={ customerAddress=it }, label="Address", icon=Icons.Rounded.LocationOn, singleLine=false, minLines=2)
         AppField(value=customerEmail, onValueChange={ customerEmail=it }, label="Email (optional)", keyboardType=KeyboardType.Email, icon=Icons.Rounded.Email)
         val saveCustomerAction: () -> Unit = { if(!canManageRecords()) { denyPermission("edit customer records") } else lifecycleScope.launch {
         val ageValidation = validateCustomerAge(customerAge)
         val ageValue = ageValidation.age
         if (customerName.isBlank()) toast("Customer name is required")
         else if (!ageValidation.isValid) toast("Enter a valid age between 0 and 120")
         else if (selectedCustomerId == null) {
          runCatching { db.dao().addCustomer(Customer(name=customerName.trim(), phone=customerPhone.trim(), address=customerAddress.trim(), age=ageValue, email=customerEmail.trim())) }
           .onSuccess { customerName=""; customerPhone=""; customerAddress=""; customerAge=""; customerEmail=""; toast("Customer saved") }
           .onFailure { toast(it.message ?: "Could not save customer") }
         } else {
          val customerId = selectedCustomerId
          if (customerId == null) {
           toast("Customer selection expired. Please select the customer again.")
          } else {
           runCatching {
            val existingCustomer=db.dao().customer(customerId) ?: error("Customer no longer exists. Refresh the list and try again.")
            db.dao().updateCustomer(existingCustomer.copy(name=customerName.trim(), phone=customerPhone.trim(), address=customerAddress.trim(), age=ageValue, email=customerEmail.trim()))
           }.onSuccess {
            selectedCustomerId=null; customerName=""; customerPhone=""; customerAddress=""; customerAge=""; customerEmail=""; toast("Customer updated")
           }.onFailure { error ->
            toast("Could not update customer: ${error.message ?: "Please try again"}")
           }
          }
         }
        } }
         PrimaryAction(text=if(selectedCustomerId==null) "Save customer" else "Update customer", onClick={
          customerTouched=true
          val problem = (if(customerName.isBlank()) "Enter the customer name" else null) ?: Validation.phoneError(customerPhone) ?: Validation.ageError(customerAge)
          if(problem!=null) toast(problem) else { saveCustomerAction(); customerTouched=false }
         }, icon=Icons.Rounded.Check)
         if(selectedCustomerId!=null) SecondaryAction(text="Cancel edit", onClick={ selectedCustomerId=null; customerName=""; customerPhone=""; customerAddress=""; customerAge=""; customerEmail="" })
        }
        Spacer(Modifier.height(20.dp))
        Text("Saved customers (${customers.size})", style=MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        AppField(value=customerSearch, onValueChange={ customerSearch=it }, label="Search by name or phone", icon=Icons.Rounded.Search)
        Spacer(Modifier.height(8.dp))
        val shownCustomers = customers.filter { customerSearch.isBlank() || it.name.contains(customerSearch,true) || it.phone.contains(customerSearch,true) }
        if(shownCustomers.isEmpty()) EmptyState(Icons.Rounded.People, "No customers found", "Saved customers will appear here.")
        shownCustomers.forEach { customer ->
         RecordCard(modifier=Modifier.padding(vertical=4.dp)) {
          Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
           AvatarBadge(customer.name)
           Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(customer.name, style=MaterialTheme.typography.titleMedium)
            Text(listOfNotNull(customer.phone.takeIf { it.isNotBlank() }, customer.age?.let { "Age $it" }, customer.address.takeIf { it.isNotBlank() }, customer.email.takeIf { it.isNotBlank() }).joinToString("  ·  ").ifBlank { "No additional details" }, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
           }
           if(canManageRecords()) IconButton(onClick={
             // Keep this click handler synchronous and side-effect-light: only copy the
             // selected record into stable screen state. Scrolling is handled by LaunchedEffect.
             selectedCustomerId = customer.id
             customerName = customer.name
             customerPhone = customer.phone
             customerAddress = customer.address
             customerAge = customer.age?.toString().orEmpty()
             customerEmail = customer.email
             customerTouched = false
            }) { Icon(Icons.Rounded.Edit, contentDescription="Edit customer", tint=MaterialTheme.colorScheme.primary) }
           if(canDeleteRecords()) IconButton(onClick={ if(!canDeleteRecords()) denyPermission("delete customers") else lifecycleScope.launch {
            val hasInvoices=db.dao().customerInvoiceCount(customer.id)>0
            val hasPrescriptions=db.dao().customerPrescriptionCount(customer.id)>0
            if(hasInvoices || hasPrescriptions) toast("Cannot delete: customer has invoice or prescription history")
            else runCatching { db.dao().deleteCustomer(customer) }.onSuccess { toast("Customer deleted") }.onFailure { toast("Could not delete customer") }
           } }) { Icon(Icons.Rounded.Delete, contentDescription="Delete customer", tint=MaterialTheme.colorScheme.error) }
          }
         }
        }
        Text("Customers with bills or prescriptions are kept and cannot be deleted.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
       }
       "Products" -> {
        var productTouched by remember { mutableStateOf(false) }
        ScreenHeader(if(selectedProductId==null) "Products & stock" else "Edit product", "Frames, lenses, sunglasses and accessories")
        Spacer(Modifier.height(12.dp))
        SectionCard(title=if(selectedProductId==null) "Add a product" else "Update product / stock", icon=Icons.Rounded.Inventory2) {
         AppField(value=productName, onValueChange={ productName=it }, label="Product / frame / lens name *", isError=productTouched && productName.isBlank(), supportingText=if(productTouched && productName.isBlank()) "Enter the product name" else null)
         Text("Category", style=MaterialTheme.typography.labelLarge)
         ChoiceChips(options=listOf("Frame","Lens","Sunglasses","Contact Lens","Accessory"), selected=productCategory, onSelect={ productCategory=it })
         AppField(value=productCategory, onValueChange={ productCategory=it }, label="Category (or type your own)")
         AppField(value=productSku, onValueChange={ productSku=it }, label="SKU (optional)")
         Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
          AppField(value=productPrice, onValueChange={ productPrice=it }, label="Selling price *", modifier=Modifier.weight(1f), prefix="₹", keyboardType=KeyboardType.Decimal, isError=productTouched && Validation.amountError(productPrice, "Selling price", positive=true)!=null, supportingText=if(productTouched) Validation.amountError(productPrice, "Selling price", positive=true) else null)
          AppField(value=productCost, onValueChange={ productCost=it }, label="Purchase cost", modifier=Modifier.weight(1f), prefix="₹", keyboardType=KeyboardType.Decimal, isError=productTouched && Validation.amountError(productCost, "Purchase cost", allowBlank=true)!=null, supportingText=if(productTouched) Validation.amountError(productCost, "Purchase cost", allowBlank=true) else null)
         }
         Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
          AppField(value=productStock, onValueChange={ productStock=it }, label="Stock quantity", modifier=Modifier.weight(1f), keyboardType=KeyboardType.Number, isError=productTouched && Validation.wholeNumberError(productStock, "Stock quantity")!=null, supportingText=if(productTouched) Validation.wholeNumberError(productStock, "Stock quantity") else null)
          AppField(value=productLowStock, onValueChange={ productLowStock=it }, label="Low stock alert at", modifier=Modifier.weight(1f), keyboardType=KeyboardType.Number, isError=productTouched && Validation.wholeNumberError(productLowStock, "Low stock alert")!=null, supportingText=if(productTouched) Validation.wholeNumberError(productLowStock, "Low stock alert") else null)
         }
         val saveProductAction: () -> Unit = { if(!canManageRecords()) { denyPermission("manage inventory") } else lifecycleScope.launch {
         val pricePaise = Money.parseRupeesToPaise(productPrice)
         val costPaise = Money.parseRupeesToPaise(productCost, blankAs=0L)
         val stock = productStock.toIntOrNull()
         val low = productLowStock.toIntOrNull()
         if (productName.isBlank() || pricePaise == null || pricePaise <= 0L || costPaise == null || stock == null || stock < 0 || low == null || low < 0) toast("Enter valid product, selling/purchase price and stock")
         else if (selectedProductId == null) {
          runCatching { db.dao().addProduct(Product(name=productName.trim(), sku=productSku.trim(), pricePaise=pricePaise, stock=stock, lowStock=low, costPaise=costPaise, category=productCategory.trim())) }
           .onSuccess { productName=""; productSku=""; productPrice=""; productCost="0"; productCategory=""; productStock="1"; productLowStock="3"; toast("Product saved") }
           .onFailure { toast(it.message ?: "Could not save product") }
         } else {
          val id=selectedProductId
          if(id==null) toast("Product selection expired. Select the product again.")
          else runCatching {
           val existingProduct=db.dao().product(id) ?: error("Product no longer exists. Refresh inventory and try again.")
           db.dao().updateProduct(existingProduct.copy(name=productName.trim(), sku=productSku.trim(), pricePaise=pricePaise, stock=stock, lowStock=low, costPaise=costPaise, category=productCategory.trim()))
          }
           .onSuccess { selectedProductId=null; productName=""; productSku=""; productPrice=""; productCost="0"; productCategory=""; productStock="1"; productLowStock="3"; toast("Product and stock updated") }
           .onFailure { toast(it.message ?: "Could not update product") }
         }
        } }
         PrimaryAction(text=if(selectedProductId==null) "Save product" else "Update product / stock", onClick={
          productTouched=true
          val problem = (if(productName.isBlank()) "Enter the product name" else null) ?: Validation.amountError(productPrice, "Selling price", positive=true) ?: Validation.amountError(productCost, "Purchase cost", allowBlank=true) ?: Validation.wholeNumberError(productStock, "Stock quantity") ?: Validation.wholeNumberError(productLowStock, "Low stock alert")
          if(problem!=null) toast(problem) else { saveProductAction(); productTouched=false }
         }, icon=Icons.Rounded.Check)
         if(selectedProductId!=null) SecondaryAction(text="Cancel edit", onClick={ selectedProductId=null; productName=""; productSku=""; productPrice=""; productCost="0"; productCategory=""; productStock="1"; productLowStock="3" })
        }
        Spacer(Modifier.height(20.dp))
        Text("Inventory (${products.size})", style=MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        AppField(value=productSearch, onValueChange={ productSearch=it }, label="Search by name, SKU or category", icon=Icons.Rounded.Search)
        Spacer(Modifier.height(8.dp))
        val shownProducts = products.filter { productSearch.isBlank() || it.name.contains(productSearch,true) || it.sku.contains(productSearch,true) || it.category.contains(productSearch,true) }
        if(shownProducts.isEmpty()) EmptyState(Icons.Rounded.Inventory2, "No products found", "Add your first frame or lens above.")
        shownProducts.forEach { product ->
         RecordCard(modifier=Modifier.padding(vertical=4.dp)) {
          Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
           Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(product.name, style=MaterialTheme.typography.titleMedium)
            Text(product.category.ifBlank { "Uncategorised" } + (if(product.sku.isNotBlank()) "  ·  SKU ${product.sku}" else ""), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
             StatusPill("Sell ${formatRupees(product.pricePaise)}", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
             StatusPill("Cost ${formatRupees(product.costPaise)}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
           }
           Column(horizontalAlignment=Alignment.End, verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("${product.stock}", style=MaterialTheme.typography.headlineSmall)
            if(product.stock<=product.lowStock) StatusPill("LOW STOCK", Brand.WarningBg, Brand.Warning) else StatusPill("IN STOCK", Brand.SuccessBg, Brand.Success)
           }
          }
          Row(horizontalArrangement=Arrangement.End, modifier=Modifier.fillMaxWidth()) {
           if(canManageRecords()) TextButton(onClick={ selectedProductId=product.id; productName=product.name; productSku=product.sku; productPrice="%.2f".format(product.pricePaise/100.0); productCost="%.2f".format(product.costPaise/100.0); productCategory=product.category; productStock=product.stock.toString(); productLowStock=product.lowStock.toString(); lifecycleScope.launch { mainScroll.animateScrollTo(0) } }) { Icon(Icons.Rounded.Edit, contentDescription=null, modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit") }
           if(canDeleteRecords()) TextButton(onClick={ if(!canDeleteRecords()) denyPermission("delete products") else lifecycleScope.launch {
            if(db.dao().productInvoiceItemCount(product.id)>0) toast("Cannot delete: product appears on invoice history")
            else runCatching { db.dao().deleteProduct(product) }.onSuccess { toast("Product deleted") }.onFailure { toast("Could not delete product") }
           } }) { Icon(Icons.Rounded.Delete, contentDescription=null, modifier=Modifier.size(18.dp), tint=MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp)); Text("Delete", color=MaterialTheme.colorScheme.error) }
          }
         }
        }
        Text("Products used on bills cannot be deleted.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
       }
       "New Bill" -> {
        val draftSubtotal = effectiveBillLines.sumOf { it.subtotalPaise }
        val draftDiscount = effectiveBillLines.sumOf { it.discountPaise }
        val draftTax = effectiveBillLines.sumOf { it.taxPaise }
        val draftTotal = effectiveBillLines.sumOf { it.total }
        val draftPaid = Money.parseRupeesToPaise(billPaid, blankAs=0L) ?: 0L
        val draftBalance = (draftTotal - draftPaid).coerceAtLeast(0)
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.CenterVertically) {
         ScreenHeader(if(editingInvoiceId==null) "New bill" else "Edit bill", "Customer · order · items · payment", Modifier.weight(1f))
         IconButton(onClick={
          val voiceIntent=Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
           putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
           putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
           putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Customer, item, amount ya advance boliye")
          }
          runCatching { voiceBillingLauncher.launch(voiceIntent) }
           .onFailure { Toast.makeText(this@MainActivity, "Voice input is not available on this device.", Toast.LENGTH_LONG).show() }
         }) { Icon(Icons.Rounded.Mic, contentDescription="Fill bill using voice") }
         FilledTonalButton(onClick={ showBillPreview=true }, enabled=effectiveBillLines.isNotEmpty()) {
          Icon(Icons.Rounded.Visibility, contentDescription=null, modifier=Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text("Preview")
         }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(20.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primary)) {
         Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
          Text(if(effectiveBillLines.isEmpty()) "BILL TOTAL" else "BILL TOTAL · ${effectiveBillLines.size} ${if(effectiveBillLines.size==1) "item" else "items"}", style=MaterialTheme.typography.labelLarge, color=Color(0xFFBDD8F4))
          Text(formatRupees(draftTotal), style=MaterialTheme.typography.headlineMedium, color=Color.White)
          Text(if(effectiveBillLines.isEmpty()) "Add a product or custom item to begin." else "Balance after payment ${formatRupees(draftBalance)}", style=MaterialTheme.typography.bodyMedium, color=Color(0xFFD9E7F6))
         }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title="Customer", step=1, subtitle="Pick a saved customer or enter a new one") {
         AppField(value=billCustomerSearch, onValueChange={ billCustomerSearch=it }, label="Find saved customer", placeholder="Search by name or phone", icon=Icons.Rounded.Search)
         val matchedCustomers = customers.filter { billCustomerSearch.isNotBlank() && (it.name.contains(billCustomerSearch, true) || it.phone.contains(billCustomerSearch, true)) }.take(4)
         matchedCustomers.forEach { customer ->
          RecordCard(onClick={ billCustomerId=customer.id.toString(); billCustomer=customer.name; billPhone=customer.phone; billCustomerAddress=customer.address; billCustomerAge=customer.age?.toString().orEmpty(); billCustomerEmail=customer.email; billPrescriptionId=""; billCustomerSearch=customer.name }) {
           Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            AvatarBadge(customer.name)
            Column(Modifier.weight(1f)) {
             Text(customer.name, style=MaterialTheme.typography.titleSmall)
             Text(customer.phone.ifBlank { "No phone" }, style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
           }
          }
         }
         Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
          AppField(value=billCustomer, onValueChange={ billCustomer=it }, label="Customer name *", modifier=Modifier.weight(1.4f), icon=Icons.Rounded.Person, isError=billTouched && billCustomer.isBlank() && selectedBillCustomer==null, supportingText=if(billTouched && billCustomer.isBlank() && selectedBillCustomer==null) "Required" else null)
          AppField(value=billPhone, onValueChange={ billPhone=it }, label="Phone", modifier=Modifier.weight(1f), keyboardType=KeyboardType.Phone, isError=billTouched && Validation.phoneError(billPhone)!=null, supportingText=if(billTouched) Validation.phoneError(billPhone) else null)
         }
         Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
          AppField(value=billCustomerAddress, onValueChange={ billCustomerAddress=it }, label="Address (optional)", modifier=Modifier.weight(1.4f), icon=Icons.Rounded.LocationOn)
          AppField(value=billCustomerAge, onValueChange={ billCustomerAge=it.filter(Char::isDigit).take(3) }, label="Age", modifier=Modifier.weight(1f), keyboardType=KeyboardType.Number)
         }
         AppField(value=billCustomerEmail, onValueChange={ billCustomerEmail=it }, label="Email (optional)", keyboardType=KeyboardType.Email, icon=Icons.Rounded.Email)
         if(billCustomerId.isNotBlank()) StatusPill("Saved customer selected", Brand.SuccessBg, Brand.Success)
         OutlinedButton(onClick={
          if (savingCustomerForPrescription) Unit
          else if (!canManageRecords()) denyPermission("save customer and create prescription")
          else {
           val ageValue = billCustomerAge.toIntOrNull()
           val phoneError = Validation.phoneError(billPhone)
           when {
            billCustomer.isBlank() -> toast("Enter the customer name first")
            phoneError != null -> toast(phoneError)
            billCustomerAge.isNotBlank() && (ageValue == null || ageValue !in 0..120) -> toast("Age must be between 0 and 120")
            else -> {
             savingCustomerForPrescription = true
             lifecycleScope.launch {
              try {
               val saved = BillingRepository(db).saveCustomer(CustomerInput(id=billCustomerId.toLongOrNull(), name=billCustomer.trim(), phone=billPhone.trim(), address=billCustomerAddress.trim(), age=ageValue, email=billCustomerEmail.trim()))
               billCustomerId=saved.id.toString(); billCustomer=saved.name; billPhone=saved.phone
               prescriptionCustomerId=saved.id.toString(); returnToBillAfterPrescription=true; editingPrescriptionId=null
               rightSphere="";rightCylinder="";rightAxis="";rightAdd="";leftSphere="";leftCylinder="";leftAxis="";leftAdd="";pd="";prescriptionNotes=""
               section="Prescription"; toast("Customer saved. Enter the eye prescription.")
              } catch (e: kotlinx.coroutines.CancellationException) { throw e }
              catch (e: Exception) { toast(e.message ?: "Could not save customer") }
              finally { savingCustomerForPrescription=false }
             }
            }
           }
          }
         }, modifier=Modifier.fillMaxWidth(), enabled=!savingCustomerForPrescription) {
          Text(if(savingCustomerForPrescription) "Saving customer…" else "Save customer & add prescription")
         }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title="Order details", step=2, subtitle="Lens type, delivery date and prescription") {
         Text("Glass / lens type", style=MaterialTheme.typography.labelLarge)
         ChoiceChips(options=listOf("Single Vision","Bifocal","Progressive","Photochromic","Blue Cut","Sunglasses","Frame Only","Contact Lens"), selected=billGlassType, onSelect={ billGlassType=it })
         AppField(value=billGlassType, onValueChange={ billGlassType=it }, label="Glass type (or type your own)")
         DateInputField(value=billDeliveryDate, onValueChange={ billDeliveryDate=it }, label="Expected delivery date", displayFormat="dd/MM/yyyy", placeholder="e.g. 05/11/2026", supportingText="Optional for walk-in sales; fill for orders awaiting lens fitting.", modifier=Modifier.fillMaxWidth())
         AppField(value=billEyeTestFee, onValueChange={ billEyeTestFee=it }, label="Eye test / prescription charge (₹)", placeholder="e.g. 200", keyboardType=KeyboardType.Decimal, supportingText="Optional. Added as a non-stock invoice item; works even when no product is sold.")
         if (billCustomerId.toLongOrNull() != null) {
          Text("Eye prescription (optional)", style=MaterialTheme.typography.labelLarge)
          if (billPrescriptions.isEmpty()) Text("No saved prescription for this customer. You can still bill sunglasses or other products.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
          billPrescriptions.forEach { rx ->
           val rxSelected = billPrescriptionId == rx.id.toString()
           RecordCard(onClick={ billPrescriptionId=rx.id.toString() }) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
             Icon(if(rxSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, contentDescription=null, tint=if(rxSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
             Text("Prescription · " + java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(rx.createdAt)), style=MaterialTheme.typography.titleSmall)
            }
           }
          }
          selectedBillPrescription?.let { rx ->
           Surface(shape=RoundedCornerShape(14.dp), color=MaterialTheme.colorScheme.secondaryContainer, modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(2.dp)) {
             Text("Prescription attached to this bill", style=MaterialTheme.typography.titleSmall, color=MaterialTheme.colorScheme.onSecondaryContainer)
             TextButton(onClick={ billPrescriptionExpanded=!billPrescriptionExpanded }) { Text(if(billPrescriptionExpanded) "Hide Prescription" else "See Prescription") }
             if(billPrescriptionExpanded) {
              Text("Right eye (OD): SPH ${rx.rightSphere.ifBlank{"—"}} · CYL ${rx.rightCylinder.ifBlank{"—"}} · Axis ${rx.rightAxis.ifBlank{"—"}} · ADD ${rx.rightAdd.ifBlank{"—"}}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSecondaryContainer)
              Text("Left eye (OS): SPH ${rx.leftSphere.ifBlank{"—"}} · CYL ${rx.leftCylinder.ifBlank{"—"}} · Axis ${rx.leftAxis.ifBlank{"—"}} · ADD ${rx.leftAdd.ifBlank{"—"}}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSecondaryContainer)
              Text("PD: ${rx.pd.ifBlank{"—"}}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSecondaryContainer)
              if(rx.notes.isNotBlank()) Text("Notes: ${rx.notes}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSecondaryContainer)
             }
             TextButton(onClick={ billPrescriptionId=""; billPrescriptionSummary=""; billPrescriptionExpanded=false }) { Text("Remove prescription") }
            }
           }
          }
         }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title="Add items", step=3, subtitle="Tap a product to fill the item, adjust, then add it") {
         AppField(value=billItemSearch, onValueChange={ billItemSearch=it }, label="Search frame / lens / product", icon=Icons.Rounded.Search)
         products.filter { billItemSearch.isBlank() || it.name.contains(billItemSearch, true) || it.sku.contains(billItemSearch, true) || it.category.contains(billItemSearch, true) }.take(6).forEach { product ->
          RecordCard(onClick={ billProductId=product.id.toString(); billDescription=product.name; billRate="%.2f".format(product.pricePaise/100.0); billItemSearch=product.name }) {
           Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
             Text(product.name, style=MaterialTheme.typography.titleSmall)
             Text(formatRupees(product.pricePaise), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(product.stock<=product.lowStock) StatusPill("Stock ${product.stock}", Brand.WarningBg, Brand.Warning) else StatusPill("Stock ${product.stock}", Brand.SuccessBg, Brand.Success)
           }
          }
         }
         AppField(value=billDescription, onValueChange={ billDescription=it }, label="Item / frame / lens description *")
         Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
          AppField(value=billQty, onValueChange={ billQty=it }, label="Qty", modifier=Modifier.weight(0.7f), keyboardType=KeyboardType.Number)
          AppField(value=billRate, onValueChange={ billRate=it }, label="Rate", modifier=Modifier.weight(1f), prefix="₹", keyboardType=KeyboardType.Decimal)
         }
         Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
          AppField(value=billDiscount, onValueChange={ billDiscount=it }, label="Discount", modifier=Modifier.weight(1f), prefix="₹", keyboardType=KeyboardType.Decimal)
          AppField(value=billTax, onValueChange={ billTax=it }, label="Tax", modifier=Modifier.weight(1f), prefix="₹", keyboardType=KeyboardType.Decimal)
         }
         PrimaryAction(text="Add item to bill", onClick={
          billTouched=true
          when(val item=pendingItem) {
           is PendingItem.Ready -> { billLines=billLines+item.line; billProductId=""; billDescription=""; billQty="1"; billRate=""; billDiscount="0"; billTax="0"; billTouched=false; toast("Item added to bill") }
           is PendingItem.Invalid -> toast(item.message)
           PendingItem.None -> toast("Enter the item description and rate")
          }
         }, icon=Icons.Rounded.Add)
         if(pendingItem is PendingItem.Invalid) Text((pendingItem as PendingItem.Invalid).message, style=MaterialTheme.typography.bodySmall, color=if(billTouched) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
         if(pendingItem is PendingItem.Ready) Text("This item will be included automatically when you create the bill.", style=MaterialTheme.typography.bodySmall, color=Brand.Success)
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title="Bill items (${effectiveBillLines.size})", step=4) {
         if(effectiveBillLines.isEmpty()) EmptyState(Icons.Rounded.Receipt, "No items yet", "Items you add will appear here.")
         effectiveBillLines.forEachIndexed { index, line ->
          Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
           Column(Modifier.weight(1f)) {
            Text("${index+1}. ${line.description}", style=MaterialTheme.typography.titleSmall)
            Text("${line.qty} × ${formatRupees(line.ratePaise)}" + (if(line.discountPaise>0) "  ·  Disc ${formatRupees(line.discountPaise)}" else "") + (if(line.taxPaise>0) "  ·  Tax ${formatRupees(line.taxPaise)}" else ""), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
           }
           Text(formatRupees(line.total), style=MaterialTheme.typography.titleSmall)
           if(index < billLines.size) IconButton(onClick={ billLines=billLines.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Rounded.Delete, contentDescription="Remove item", tint=MaterialTheme.colorScheme.error) } else StatusPill("CURRENT", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
          }
          if(index<effectiveBillLines.lastIndex) HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
         }
         if(effectiveBillLines.isNotEmpty()) {
          HorizontalDivider(color=MaterialTheme.colorScheme.outline)
          AmountRow("Items subtotal", formatRupees(draftSubtotal))
          AmountRow("Discount", "− " + formatRupees(draftDiscount))
          AmountRow("Tax", formatRupees(draftTax))
          AmountRow("Grand total", formatRupees(draftTotal), emphasize=true, valueColor=MaterialTheme.colorScheme.primary)
         }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title="Payment", step=5, subtitle="Amount received now") {
         AppField(value=billPaid, onValueChange={ billPaid=it }, label="Amount received", prefix="₹", keyboardType=KeyboardType.Decimal, isError=Money.parseRupeesToPaise(billPaid, blankAs=0L)==null, supportingText=if(Money.parseRupeesToPaise(billPaid, blankAs=0L)==null) "Enter a valid amount" else null)
         Text("Payment method", style=MaterialTheme.typography.labelLarge)
         ChoiceChips(options=listOf("Cash","Online","Other"), selected=billPaymentMethod, onSelect={ billPaymentMethod=it })
         AmountRow("Balance due", formatRupees(draftBalance), emphasize=true, valueColor=if(draftBalance>0) Brand.Warning else Brand.Success)
        }
        Spacer(Modifier.height(16.dp))
        SecondaryAction(text="Preview bill before saving", onClick={ showBillPreview=true }, icon=Icons.Rounded.Visibility, enabled=effectiveBillLines.isNotEmpty())
        Spacer(Modifier.height(8.dp))
        val saveBillAction: () -> Unit = {
         if(!savingBill) {
          billTouched=true
          val pending=pendingItem
          val paidPaise=Money.parseRupeesToPaise(billPaid, blankAs=0L)
          val age=billCustomerAge.toIntOrNull()
          val typedName=billCustomer.trim()
          val phoneProblem=Validation.phoneError(billPhone)
          when {
           typedName.isBlank() && selectedBillCustomer==null -> toast("Enter the customer name")
           phoneProblem!=null -> toast(phoneProblem)
           billCustomerAge.isNotBlank() && (age==null || age !in 0..120) -> toast("Age must be between 0 and 120")
           billEyeTestFee.isNotBlank() && (eyeTestFeePaise == null || eyeTestFeePaise < 0L) -> toast("Eye test charge is not a valid amount")
           pending is PendingItem.Invalid -> toast(pending.message)
           effectiveBillLines.isEmpty() -> toast("Add an item: enter the description and rate")
           paidPaise==null -> toast("Amount received is not a valid amount")
           else -> {
            savingBill=true
            lifecycleScope.launch {
             try {
              val finalName=typedName.ifBlank { selectedBillCustomer?.name.orEmpty() }
              val finalPhone=billPhone.trim().ifBlank { selectedBillCustomer?.phone.orEmpty() }
              val draft=NewBill(
               customerId=billCustomerId.toLongOrNull(), customerName=finalName, phone=finalPhone,
               lines=effectiveBillLines, paidPaise=paidPaise, method=billPaymentMethod,
               glassType=billGlassType.trim(), deliveryDate=billDeliveryDate.trim(),
               prescriptionId=selectedBillPrescription?.id ?: billPrescriptionId.toLongOrNull(),
               prescriptionSummary=selectedBillPrescription?.let { rx -> listOf(
             "Right eye (OD): SPH ${rx.rightSphere.ifBlank{"—"}}, CYL ${rx.rightCylinder.ifBlank{"—"}}, Axis ${rx.rightAxis.ifBlank{"—"}}, ADD ${rx.rightAdd.ifBlank{"—"}}",
             "Left eye (OS): SPH ${rx.leftSphere.ifBlank{"—"}}, CYL ${rx.leftCylinder.ifBlank{"—"}}, Axis ${rx.leftAxis.ifBlank{"—"}}, ADD ${rx.leftAdd.ifBlank{"—"}}",
             "PD: ${rx.pd.ifBlank{"—"}}", rx.notes.takeIf { it.isNotBlank() }?.let { "Notes: $it" }
            ).filterNotNull().joinToString("\n") } ?: billPrescriptionSummary,
               customer=CustomerInput(id=billCustomerId.toLongOrNull(), name=finalName, phone=finalPhone, address=billCustomerAddress, age=age, email=billCustomerEmail.trim())
              )
              val id=if(editingInvoiceId==null) BillingRepository(db).createBill(draft) else { BillingRepository(db).updateBill(editingInvoiceId!!,draft); editingInvoiceId!! }
              toast(if(editingInvoiceId==null) "Invoice created: #$id" else "Invoice updated")
              section="Bills"; editingInvoiceId=null; billCustomer=""; billCustomerId=""; billPhone=""; billCustomerAddress=""; billCustomerAge=""; billCustomerEmail=""; billCustomerSearch=""; billLines=emptyList(); billProductId=""; billDescription=""; billQty="1"; billRate=""; billDiscount="0"; billTax="0"; billPaid="0"; billPaymentMethod="Cash"; billPrescriptionId=""; billPrescriptionSummary=""; billEyeTestFee=""; billGlassType=""; billDeliveryDate=""; billTouched=false
             } catch(e: kotlinx.coroutines.CancellationException) {
              throw e
             } catch(e: Exception) {
              toast(e.message ?: "Could not create invoice")
             } finally {
              savingBill=false
             }
            }
           }
          }
         }
        }
        PrimaryAction(text=if(savingBill) "Saving…" else if(editingInvoiceId==null) "Create bill and deduct stock" else "Save bill changes", enabled=!savingBill, onClick=saveBillAction, icon=Icons.Rounded.Check)
        if (showVoiceSaveConfirmation) {
         AlertDialog(
          onDismissRequest={ showVoiceSaveConfirmation=false },
          title={ Text("Confirm bill save") },
          text={
           Column {
            Text("Customer: " + billCustomer.ifBlank { selectedBillCustomer?.name ?: "Not selected" })
            Text("Total: ${formatRupees(draftTotal)}")
            Text("Advance received: ${formatRupees(draftPaid)}")
            Text("Pending balance: ${formatRupees(draftBalance)}")
            Spacer(Modifier.height(8.dp))
            Text("Save this bill now?")
           }
          },
          confirmButton={ TextButton(onClick={
           showVoiceSaveConfirmation=false
           saveBillAction()
          }, enabled=!savingBill) { Text("Confirm & save") } },
          dismissButton={ TextButton(onClick={ showVoiceSaveConfirmation=false }) { Text("Cancel") } }
         )
        }
        Spacer(Modifier.height(24.dp))
       }
        "Payment" -> {
        val selectedPaymentInvoice = invoices.firstOrNull { it.id.toString() == paymentInvoiceId }
        Text("Payments & collections", style=MaterialTheme.typography.headlineSmall)
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
          OutlinedTextField(paymentReference, { paymentReference=it }, label={ Text("Reference / transaction ID (optional)") }, singleLine=true, modifier=Modifier.fillMaxWidth())
          Text("Payment method", style=MaterialTheme.typography.titleSmall)
          Row(horizontalArrangement=Arrangement.spacedBy(6.dp), modifier=Modifier.fillMaxWidth()) {
           listOf("Cash", "Online", "Other").forEach { method ->
            FilterChip(selected=paymentMethod==method, onClick={ paymentMethod=method }, label={ Text(method) }, modifier=Modifier.weight(1f))
           }
          }
          Button(onClick={ lifecycleScope.launch {
           val id=paymentInvoiceId.toLongOrNull()
           val amount=Money.parseRupeesToPaise(paymentAmount)
           if(id==null || amount==null || amount<=0L) toast("Enter a valid invoice ID and payment amount")
           else runCatching {
            if(editingPaymentId==null) BillingRepository(db).recordPayment(id,amount,paymentMethod.trim().ifBlank{"Cash"},paymentReference)
            else BillingRepository(db).updatePayment(editingPaymentId!!,amount,paymentMethod,paymentReference)
           }.onSuccess {
            toast(if(editingPaymentId==null) "Payment recorded successfully" else "Payment updated successfully")
            paymentAmount="";paymentReference="";editingPaymentId=null
           }.onFailure { toast(it.message ?: "Payment save failed") }
          } }, enabled=paymentInvoiceId.toLongOrNull()!=null && (Money.parseRupeesToPaise(paymentAmount) ?: 0L)>0L, modifier=Modifier.fillMaxWidth()) {
           Icon(Icons.Rounded.Check, contentDescription=null); Spacer(Modifier.width(8.dp)); Text(if(editingPaymentId==null) "Record payment" else "Update payment")
          }
          if(editingPaymentId!=null) TextButton(onClick={editingPaymentId=null;paymentAmount="";paymentReference=""}, modifier=Modifier.fillMaxWidth()) { Text("Cancel payment edit") }
         }
        }
        Spacer(Modifier.height(16.dp))
        Text("Choose an invoice", style=MaterialTheme.typography.titleLarge)
        invoices.forEach { inv ->
         Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable { paymentInvoiceId=inv.id.toString(); paymentAmount="";paymentReference="";editingPaymentId=null }) {
          Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement=Arrangement.spacedBy(10.dp), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
           Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color=if(inv.duePaise<=0) androidx.compose.ui.graphics.Color(0xFFE0F4E8) else androidx.compose.ui.graphics.Color(0xFFFFE8D6), modifier=Modifier.size(44.dp)) {
            Box(contentAlignment=androidx.compose.ui.Alignment.Center) { Icon(if(inv.duePaise<=0) Icons.Rounded.Check else Icons.Rounded.Payments, contentDescription=null, tint=if(inv.duePaise<=0) androidx.compose.ui.graphics.Color(0xFF176B3A) else androidx.compose.ui.graphics.Color(0xFF9A4D00)) }
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
          Column(Modifier.fillMaxWidth().padding(12.dp)) {
           Row(horizontalArrangement=Arrangement.spacedBy(10.dp), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Surface(shape=androidx.compose.foundation.shape.CircleShape, color=MaterialTheme.colorScheme.secondaryContainer, modifier=Modifier.size(40.dp)) { Box(contentAlignment=androidx.compose.ui.Alignment.Center) { Icon(Icons.Rounded.Check, contentDescription=null, tint=MaterialTheme.colorScheme.primary) } }
            Column(Modifier.weight(1f)) {
             Text(payment.method, style=MaterialTheme.typography.titleSmall)
             Text(java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(payment.paidAt)), style=MaterialTheme.typography.bodySmall)
             if(payment.reference.isNotBlank()) Text("Ref: ${payment.reference}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("+₹${"%.2f".format(payment.amountPaise/100.0)}", style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.primary)
           }
           if(canManageRecords()) Row(horizontalArrangement=Arrangement.End, modifier=Modifier.fillMaxWidth()) {
            TextButton(onClick={ editingPaymentId=payment.id;paymentInvoiceId=payment.invoiceId.toString();paymentAmount="%.2f".format(payment.amountPaise/100.0);paymentMethod=payment.method;paymentReference=payment.reference }) { Text("Edit") }
            if(canDeleteRecords()) TextButton(onClick={ paymentDeleteTarget=payment }) { Text("Delete", color=MaterialTheme.colorScheme.error) }
           }
          }
         }
        }
       }
       "Prescription" -> {
        Column(verticalArrangement=Arrangement.spacedBy(14.dp), modifier=Modifier.fillMaxWidth()) {
         Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
          Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=Alignment.CenterVertically) {
           Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color=MaterialTheme.colorScheme.primary, modifier=Modifier.size(54.dp)) {
            Box(contentAlignment=Alignment.Center) { OpticianLogo(modifier=Modifier.size(38.dp), color=androidx.compose.ui.graphics.Color.White) }
           }
           Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(if(editingPrescriptionId==null) "Eye prescription" else "Edit prescription", style=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.onPrimaryContainer)
           }
          }
         }
         Card(Modifier.fillMaxWidth()) {
          Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
           Text("1. CUSTOMER", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
           AppField(value=prescriptionCustomerId, onValueChange={ prescriptionCustomerId=it }, label="Customer ID", placeholder="Enter or select a customer below", keyboardType=KeyboardType.Number)
           val selectedPrescriptionCustomer = customers.firstOrNull { it.id.toString()==prescriptionCustomerId }
           if(selectedPrescriptionCustomer!=null) {
            Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.secondaryContainer, modifier=Modifier.fillMaxWidth()) {
             Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(2.dp)) {
              Text(selectedPrescriptionCustomer.name, style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.onSecondaryContainer)
              Text("ID #${selectedPrescriptionCustomer.id}  ·  ${selectedPrescriptionCustomer.phone}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSecondaryContainer)
             }
            }
           }
           if(customers.isEmpty()) Text("No customers added yet. Add a customer first.", style=MaterialTheme.typography.bodyMedium)
           OutlinedButton(onClick={ showPrescriptionCustomerForm=!showPrescriptionCustomerForm; if(showPrescriptionCustomerForm) { customerName="";customerPhone="";customerAddress="";customerAge="";customerEmail="" } }, modifier=Modifier.fillMaxWidth()) {
            Text(if(showPrescriptionCustomerForm) "Cancel Add Customer" else "Add Customer")
           }
           if(showPrescriptionCustomerForm) {
            AppField(value=customerName, onValueChange={ customerName=it }, label="Customer name *", icon=Icons.Rounded.Person)
            AppField(value=customerPhone, onValueChange={ customerPhone=it }, label="Phone (WhatsApp)", keyboardType=KeyboardType.Phone, icon=Icons.Rounded.Phone)
            AppField(value=customerAddress, onValueChange={ customerAddress=it }, label="Address (optional)", icon=Icons.Rounded.LocationOn)
            AppField(value=customerAge, onValueChange={ customerAge=it.filter(Char::isDigit).take(3) }, label="Age", keyboardType=KeyboardType.Number)
            AppField(value=customerEmail, onValueChange={ customerEmail=it }, label="Email (optional)", keyboardType=KeyboardType.Email, icon=Icons.Rounded.Email)
            PrimaryAction(text=if(savingCustomerForPrescription) "Saving customer…" else "Save customer", enabled=!savingCustomerForPrescription, onClick={
             val ageValue=customerAge.toIntOrNull()
             val phoneError=Validation.phoneError(customerPhone)
             when {
              customerName.isBlank() -> toast("Enter the customer name")
              phoneError!=null -> toast(phoneError)
              customerAge.isNotBlank() && (ageValue==null || ageValue !in 0..120) -> toast("Age must be between 0 and 120")
              !canManageRecords() -> denyPermission("add customer")
              else -> {
               savingCustomerForPrescription=true
               lifecycleScope.launch {
                try {
                 val saved=BillingRepository(db).saveCustomer(CustomerInput(id=null,name=customerName.trim(),phone=customerPhone.trim(),address=customerAddress.trim(),age=ageValue,email=customerEmail.trim()))
                 prescriptionCustomerId=saved.id.toString(); customerName="";customerPhone="";customerAddress="";customerAge="";customerEmail="";showPrescriptionCustomerForm=false
                 toast("Customer saved. You can now enter the prescription.")
                } catch(e: kotlinx.coroutines.CancellationException) { throw e }
                catch(e: Exception) { toast(e.message ?: "Could not save customer") }
                finally { savingCustomerForPrescription=false }
               }
              }
             }
            }, icon=Icons.Rounded.PersonAdd)
           }
           customers.forEach { customer ->
            Surface(
             modifier=Modifier.fillMaxWidth().clickable { prescriptionCustomerId=customer.id.toString() },
             shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
             color=if(prescriptionCustomerId==customer.id.toString()) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
             border=androidx.compose.foundation.BorderStroke(1.dp, if(prescriptionCustomerId==customer.id.toString()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha=0.5f))
            ) {
             Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=10.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
              Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(2.dp)) {
               Text(customer.name, style=MaterialTheme.typography.titleSmall)
               Text("#${customer.id}  ·  ${customer.phone}", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
              }
              if(prescriptionCustomerId==customer.id.toString()) Text("✓ Selected", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary)
             }
            }
           }
          }
         }
         Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
          Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
           Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color=MaterialTheme.colorScheme.primary, modifier=Modifier.size(36.dp)) { Box(contentAlignment=Alignment.Center) { Text("R", color=MaterialTheme.colorScheme.onPrimary, style=MaterialTheme.typography.titleMedium) } }
            Column(Modifier.weight(1f)) {
             Text("RIGHT EYE", style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.primary)
             Text("OD · Oculus Dexter", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
           }
           Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
            SignedDecimalField(value=rightSphere, onValueChange={ rightSphere=it }, label="SPH", placeholder="-2.00", supportingText="Sphere", modifier=Modifier.weight(1f), isError=!PrescriptionRules.isValidPower(rightSphere, -30.0, 30.0))
            SignedDecimalField(value=rightCylinder, onValueChange={ rightCylinder=it }, label="CYL", placeholder="-0.75", supportingText="Cylinder", modifier=Modifier.weight(1f), isError=!PrescriptionRules.isValidPower(rightCylinder, -15.0, 15.0))
           }
           Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
            AppField(value=rightAxis, onValueChange={ rightAxis=it }, label="AXIS", placeholder="90", supportingText="0–180°", modifier=Modifier.weight(1f), keyboardType=KeyboardType.Number, isError=!PrescriptionRules.isValidAxis(rightAxis))
            SignedDecimalField(value=rightAdd, onValueChange={ rightAdd=it }, label="ADD", placeholder="+1.50", supportingText="Addition", modifier=Modifier.weight(1f), isError=!PrescriptionRules.isValidPower(rightAdd, 0.0, 5.0))
           }
          }
         }
         Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
          Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
           Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp), color=MaterialTheme.colorScheme.secondary, modifier=Modifier.size(36.dp)) { Box(contentAlignment=Alignment.Center) { Text("L", color=MaterialTheme.colorScheme.onPrimary, style=MaterialTheme.typography.titleMedium) } }
            Column(Modifier.weight(1f)) {
             Text("LEFT EYE", style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.secondary)
             Text("OS · Oculus Sinister", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
           }
           Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
            SignedDecimalField(value=leftSphere, onValueChange={ leftSphere=it }, label="SPH", placeholder="-1.50", supportingText="Sphere", modifier=Modifier.weight(1f), isError=!PrescriptionRules.isValidPower(leftSphere, -30.0, 30.0))
            SignedDecimalField(value=leftCylinder, onValueChange={ leftCylinder=it }, label="CYL", placeholder="-0.50", supportingText="Cylinder", modifier=Modifier.weight(1f), isError=!PrescriptionRules.isValidPower(leftCylinder, -15.0, 15.0))
           }
           Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
            AppField(value=leftAxis, onValueChange={ leftAxis=it }, label="AXIS", placeholder="180", supportingText="0–180°", modifier=Modifier.weight(1f), keyboardType=KeyboardType.Number, isError=!PrescriptionRules.isValidAxis(leftAxis))
            SignedDecimalField(value=leftAdd, onValueChange={ leftAdd=it }, label="ADD", placeholder="+1.50", supportingText="Addition", modifier=Modifier.weight(1f), isError=!PrescriptionRules.isValidPower(leftAdd, 0.0, 5.0))
           }
          }
         }
         Card(Modifier.fillMaxWidth()) {
          Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
           Text("2. ADDITIONAL DETAILS", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
           AppField(value=pd, onValueChange={ pd=it }, label="Pupillary distance (PD)", placeholder="e.g. 62", supportingText="Distance in millimetres (mm)", keyboardType=KeyboardType.Decimal, isError=!PrescriptionRules.isValidPd(pd))
           AppField(value=prescriptionNotes, onValueChange={ prescriptionNotes=it }, label="Notes / lens recommendation", placeholder="Optional notes", singleLine=false, minLines=3)
           Text("Important: + and − signs matter. Copy the values exactly from the prescribing optometrist. Axis must be between 0 and 180 degrees.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
          }
         }
         Button(onClick={ if(!canManageRecords()) { denyPermission("manage prescriptions") } else lifecycleScope.launch {
          val cid=prescriptionCustomerId.toLongOrNull()
          val rightAxisValue=rightAxis.toIntOrNull()
          val leftAxisValue=leftAxis.toIntOrNull()
          val prescriptionProblem=PrescriptionRules.firstError(rightSphere, rightCylinder, rightAxis, rightAdd, leftSphere, leftCylinder, leftAxis, leftAdd, pd)
          if(cid==null || customers.none{it.id==cid}) toast("Select an existing customer")
          else if((rightAxis.isNotBlank() && (rightAxisValue==null || rightAxisValue !in 0..180)) || (leftAxis.isNotBlank() && (leftAxisValue==null || leftAxisValue !in 0..180))) toast("Axis must be a number from 0 to 180")
          else if(prescriptionProblem!=null) toast(prescriptionProblem)
          else {
           try {
           val rx = Prescription(id=editingPrescriptionId ?: 0L,customerId=cid,rightSphere=rightSphere.trim(),rightCylinder=rightCylinder.trim(),rightAxis=rightAxis.trim(),rightAdd=rightAdd.trim(),leftSphere=leftSphere.trim(),leftCylinder=leftCylinder.trim(),leftAxis=leftAxis.trim(),leftAdd=leftAdd.trim(),pd=pd.trim(),notes=prescriptionNotes.trim())
           val savedPrescriptionId = BillingRepository(db).savePrescription(rx)
           editingPrescriptionId=null
           rightSphere="";rightCylinder="";rightAxis="";rightAdd="";leftSphere="";leftCylinder="";leftAxis="";leftAdd="";pd="";prescriptionNotes=""
           if(returnToBillAfterPrescription) {
            billPrescriptionId=savedPrescriptionId.toString()
            billPrescriptionSummary=listOf(
             "Right eye (OD): SPH ${rx.rightSphere.ifBlank{"—"}}, CYL ${rx.rightCylinder.ifBlank{"—"}}, Axis ${rx.rightAxis.ifBlank{"—"}}, ADD ${rx.rightAdd.ifBlank{"—"}}",
             "Left eye (OS): SPH ${rx.leftSphere.ifBlank{"—"}}, CYL ${rx.leftCylinder.ifBlank{"—"}}, Axis ${rx.leftAxis.ifBlank{"—"}}, ADD ${rx.leftAdd.ifBlank{"—"}}",
             "PD: ${rx.pd.ifBlank{"—"}}", rx.notes.takeIf { it.isNotBlank() }?.let { "Notes: $it" }
            ).filterNotNull().joinToString("\n")
            returnToBillAfterPrescription=false
            section="New Bill"
            toast("Prescription saved and attached to this bill")
           } else toast("Prescription saved")
           } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
           } catch (e: Exception) {
            toast(e.message ?: "Could not save prescription")
           }
          }
         } }, modifier=Modifier.fillMaxWidth().height(52.dp), shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) { Text(if(editingPrescriptionId == null) "Save prescription" else "Update prescription", style=MaterialTheme.typography.titleMedium) }
         if(editingPrescriptionId != null) OutlinedButton(onClick={ editingPrescriptionId=null; rightSphere="";rightCylinder="";rightAxis="";rightAdd="";leftSphere="";leftCylinder="";leftAxis="";leftAdd="";pd="";prescriptionNotes="" }, modifier=Modifier.fillMaxWidth().height(48.dp)) { Text("Cancel editing") }
         Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
          Text("Prescription history", style=MaterialTheme.typography.titleLarge, modifier=Modifier.weight(1f))
          Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(50), color=MaterialTheme.colorScheme.secondaryContainer) { Text("${savedPrescriptions.size} saved", modifier=Modifier.padding(horizontal=10.dp, vertical=5.dp), style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onSecondaryContainer) }
         }
         if(savedPrescriptions.isEmpty()) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) { Text("No prescriptions yet", style=MaterialTheme.typography.titleSmall); Text("Select a customer above and save their first prescription.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant) } }
         savedPrescriptions.forEach { rx ->
          Card(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
           Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(rx.createdAt)), style=MaterialTheme.typography.titleSmall)
            Divider(color=MaterialTheme.colorScheme.outline.copy(alpha=0.5f))
            Text("RIGHT EYE (OD)", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary)
            Text("SPH ${rx.rightSphere.ifBlank{"—"}}    CYL ${rx.rightCylinder.ifBlank{"—"}}    AXIS ${rx.rightAxis.ifBlank{"—"}}    ADD ${rx.rightAdd.ifBlank{"—"}}", style=MaterialTheme.typography.bodyMedium)
            Text("LEFT EYE (OS)", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.secondary)
            Text("SPH ${rx.leftSphere.ifBlank{"—"}}    CYL ${rx.leftCylinder.ifBlank{"—"}}    AXIS ${rx.leftAxis.ifBlank{"—"}}    ADD ${rx.leftAdd.ifBlank{"—"}}", style=MaterialTheme.typography.bodyMedium)
            Text("PD: ${rx.pd.ifBlank{"—"}} mm", style=MaterialTheme.typography.bodyMedium)
            if(rx.notes.isNotBlank()) Text("Notes: ${rx.notes}", style=MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement=Arrangement.spacedBy(2.dp), verticalAlignment=Alignment.CenterVertically) {
             if(canManageRecords()) TextButton(onClick={ editingPrescriptionId=rx.id; prescriptionCustomerId=rx.customerId.toString(); rightSphere=rx.rightSphere;rightCylinder=rx.rightCylinder;rightAxis=rx.rightAxis;rightAdd=rx.rightAdd;leftSphere=rx.leftSphere;leftCylinder=rx.leftCylinder;leftAxis=rx.leftAxis;leftAdd=rx.leftAdd;pd=rx.pd;prescriptionNotes=rx.notes }) { Text("Edit") }
             val prescriptionCustomerName = customers.firstOrNull { it.id==rx.customerId }?.name ?: "Customer #${rx.customerId}"
             TextButton(onClick={ sharePrescriptionPdf(rx, prescriptionCustomerName) }) { Text("PDF / Share") }
             TextButton(onClick={ printPrescriptionPdf(rx, prescriptionCustomerName) }) { Text("Print") }
             if(canDeleteRecords()) TextButton(onClick={ if(!canDeleteRecords()) denyPermission("delete prescriptions") else lifecycleScope.launch {
              runCatching { BillingRepository(db).deletePrescription(rx.id) }.onSuccess { toast("Prescription deleted") }.onFailure { toast(it.message ?: "Could not delete prescription") }
             } }) { Text("Delete") }
            }
           }
          }
         }
        }
       }
       "Reports" -> {
 ScreenHeader("Reports")
 ChoiceChips(options=listOf("Collection","Sales"),selected=reportType,onSelect={reportType=it;reportQuery=""})
 SectionCard(title="Date range",icon=Icons.Rounded.DateRange) {
  DateInputField(value=reportFrom,onValueChange={reportFrom=it},label="From date",displayFormat="yyyy-MM-dd",placeholder="2026-10-01",modifier=Modifier.fillMaxWidth())
  DateInputField(value=reportTo,onValueChange={reportTo=it},label="To date",displayFormat="yyyy-MM-dd",placeholder="2026-10-30",modifier=Modifier.fillMaxWidth())
  val df=if(reportFrom.isBlank()) null else parseReportDate(reportFrom)
  val dt=if(reportTo.isBlank()) null else parseReportDate(reportTo)?.let { Calendar.getInstance().apply { timeInMillis=it; add(Calendar.DAY_OF_MONTH,1) }.timeInMillis }
  val bad=(reportFrom.isNotBlank()&&df==null)||(reportTo.isNotBlank()&&dt==null)||(df!=null&&dt!=null&&df>=dt)
  if(bad) Text("Valid date range enter karein; From date, To date se pehle honi chahiye.",color=MaterialTheme.colorScheme.error)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
   PrimaryAction("Search Report",{if(bad) toast("Please correct date range") else {reportAppliedFrom=reportFrom;reportAppliedTo=reportTo}},icon=Icons.Rounded.Search,modifier=Modifier.weight(1f),enabled=!bad)
   SecondaryAction("Clear",{reportFrom="";reportTo="";reportAppliedFrom="";reportAppliedTo=""},modifier=Modifier.weight(0.7f))
  }
 }
 val from=remember(reportAppliedFrom){if(reportAppliedFrom.isBlank()) null else parseReportDate(reportAppliedFrom)}
 val to=remember(reportAppliedTo){if(reportAppliedTo.isBlank()) null else parseReportDate(reportAppliedTo)?.let { Calendar.getInstance().apply { timeInMillis=it; add(Calendar.DAY_OF_MONTH,1) }.timeInMillis }}
 val selectedInvoices=allInvoices.filter{(from==null||it.createdAt>=from)&&(to==null||it.createdAt<to)}
 val selectedPayments=allPayments.filter{(from==null||it.paidAt>=from)&&(to==null||it.paidAt<to)}
  val invoicesById=allInvoices.associateBy { it.id }
 val cash=selectedPayments.filter{it.method.equals("Cash",true)}.sumOf{it.amountPaise}
 val online=selectedPayments.filter{it.method.equals("Online",true)}.sumOf{it.amountPaise}
 val collected=selectedPayments.sumOf{it.amountPaise}
 val sales=selectedInvoices.sumOf{it.totalPaise}
 val dueTotal=selectedInvoices.sumOf{it.duePaise}
 val advance=selectedPayments.filter{p->selectedInvoices.firstOrNull{it.id==p.invoiceId}?.deliveryDate?.isNotBlank()==true}.sumOf{it.amountPaise}
 val search=reportQuery.trim()
 val shownInvoices=selectedInvoices.filter{search.isBlank()||it.number.contains(search,true)||it.customerName.contains(search,true)||it.phone.contains(search,true)}
 val shownPayments=selectedPayments.filter{p->val inv=invoicesById[p.invoiceId];search.isBlank()||inv?.number?.contains(search,true)==true||inv?.customerName?.contains(search,true)==true||inv?.phone?.contains(search,true)==true}
 val dateFmt=java.text.SimpleDateFormat("dd MMM",java.util.Locale.getDefault())
 val collectionBars=selectedPayments.groupBy{dateFmt.format(java.util.Date(it.paidAt))}.entries.sortedBy{entry->entry.value.minOf{it.paidAt}}.takeLast(7).map{it.key to it.value.sumOf(Payment::amountPaise)}
 val salesBars=selectedInvoices.groupBy{dateFmt.format(java.util.Date(it.createdAt))}.entries.sortedBy{entry->entry.value.minOf{it.createdAt}}.takeLast(7).map{it.key to it.value.sumOf(Invoice::totalPaise)}
 if(reportType=="Collection") {
  SectionCard(title="Collection Summary",icon=Icons.Rounded.Payments) {
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){StatTile("Cash",formatRupees(cash),Icons.Rounded.Payments,Brand.Success,Brand.SuccessBg,Modifier.weight(1f));StatTile("Online",formatRupees(online),Icons.Rounded.CreditCard,MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.primaryContainer,Modifier.weight(1f))}
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){StatTile("Total Collection",formatRupees(collected),Icons.Rounded.AccountBalanceWallet,MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.primaryContainer,Modifier.weight(1f));StatTile("Advance",formatRupees(advance),Icons.Rounded.Savings,Brand.Warning,Brand.WarningBg,Modifier.weight(1f))}
   StatTile("Balance / Due",formatRupees(dueTotal),Icons.Rounded.HourglassBottom,Brand.Danger,Brand.DangerBg,Modifier.fillMaxWidth())
  }
  SectionCard(title="Collection Bar Chart",icon=Icons.Rounded.BarChart){if(collectionBars.isEmpty()) EmptyState(Icons.Rounded.BarChart,"No collection data","Selected period mein payment nahi hai.") else ReportBarChart(collectionBars)}
  SectionCard(title="Collection Details",icon=Icons.Rounded.Receipt) {
   AppField(value=reportQuery,onValueChange={reportQuery=it},label="Search customer, bill number or phone",icon=Icons.Rounded.Search)
   if(shownPayments.isEmpty()) EmptyState(Icons.Rounded.Payments,"No collections found","Date range ya search change karein.")
   shownPayments.forEach{p->val inv=invoicesById[p.invoiceId];RecordCard(Modifier.padding(vertical=3.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(inv?.customerName?: "Invoice #${p.invoiceId}",style=MaterialTheme.typography.titleSmall);Text("${inv?.number?: "Bill"} · "+java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a",java.util.Locale.getDefault()).format(java.util.Date(p.paidAt)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};StatusPill(p.method,if(p.method.equals("Cash",true)) Brand.SuccessBg else MaterialTheme.colorScheme.primaryContainer,if(p.method.equals("Cash",true)) Brand.Success else MaterialTheme.colorScheme.primary)}
    AmountRow("Received",formatRupees(p.amountPaise),emphasize=true)
   }}
   Text("Advance is included in Total Collection, not added again. It represents payments against invoices with an expected delivery date.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 } else {
  val todayStart=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
  val todayEnd=Calendar.getInstance().apply{add(Calendar.DAY_OF_MONTH,1);set(Calendar.HOUR_OF_DAY,0);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis
  val todaySale=allInvoices.filter{it.createdAt>=todayStart&&it.createdAt<todayEnd}.sumOf{it.totalPaise}
  SectionCard(title="Sales Summary",icon=Icons.Rounded.Assessment) {
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){StatTile("Total Sales",formatRupees(sales),Icons.Rounded.Assessment,MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.primaryContainer,Modifier.weight(1f));StatTile("Total Bills","${selectedInvoices.size}",Icons.Rounded.Receipt,MaterialTheme.colorScheme.secondary,MaterialTheme.colorScheme.secondaryContainer,Modifier.weight(1f))}
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){StatTile("Average Bill",formatRupees(if(selectedInvoices.isEmpty())0 else sales/selectedInvoices.size),Icons.Rounded.Calculate,Brand.Success,Brand.SuccessBg,Modifier.weight(1f));StatTile("Paid Amount",formatRupees(selectedInvoices.sumOf{it.paidPaise}),Icons.Rounded.Payments,Brand.Success,Brand.SuccessBg,Modifier.weight(1f))}
   StatTile("Outstanding Amount",formatRupees(dueTotal),Icons.Rounded.AccountBalanceWallet,Brand.Warning,Brand.WarningBg,Modifier.fillMaxWidth())
   StatTile("Today's Sale",formatRupees(todaySale),Icons.Rounded.Today,MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.primaryContainer,Modifier.fillMaxWidth())
  }
  SectionCard(title="Sales Bar Chart",icon=Icons.Rounded.BarChart){if(salesBars.isEmpty()) EmptyState(Icons.Rounded.BarChart,"No sales data","Selected period mein invoices nahi hain.") else ReportBarChart(salesBars)}
  SectionCard(title="Sales Details",icon=Icons.Rounded.Receipt) {
   AppField(value=reportQuery,onValueChange={reportQuery=it},label="Search customer, bill number or phone",icon=Icons.Rounded.Search)
   if(shownInvoices.isEmpty()) EmptyState(Icons.Rounded.Receipt,"No sales found","Date range ya search change karein.")
   shownInvoices.forEach{inv->RecordCard(Modifier.padding(vertical=3.dp)){
    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(inv.customerName,style=MaterialTheme.typography.titleSmall);Text(inv.number+" · "+java.text.SimpleDateFormat("dd MMM yyyy",java.util.Locale.getDefault()).format(java.util.Date(inv.createdAt)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};PaymentBadge(inv.duePaise,inv.paidPaise)}
    AmountRow("Total Sale",formatRupees(inv.totalPaise));AmountRow("Paid",formatRupees(inv.paidPaise));AmountRow("Balance",formatRupees(inv.duePaise),valueColor=if(inv.duePaise>0) Brand.Warning else Brand.Success)
   }}
   PrimaryAction("Export sales to CSV",{InvoiceCsv.share(this@MainActivity,shownInvoices)},icon=Icons.Rounded.FileDownload)
  }
 }
}
"Users" -> {
        ScreenHeader("Users", "Local accounts for shop staff. Only the owner can manage accounts.")
        Spacer(Modifier.height(12.dp))
        SectionCard(title=if(editingStaffUsername==null) "Create staff account" else "Update staff account", icon=Icons.Rounded.PersonAdd) {
         if(editingStaffUsername==null) AppField(value=newStaffName, onValueChange={ newStaffName=it }, label="Staff username", icon=Icons.Rounded.Person)
         else Text("Username: $newStaffName", style=MaterialTheme.typography.titleSmall)
         AppField(value=newStaffPassword, onValueChange={ newStaffPassword=it }, label=if(editingStaffUsername==null) "Temporary password (8+ characters)" else "New password (leave blank to keep current)", icon=Icons.Rounded.Lock)
         Text("Role", style=MaterialTheme.typography.labelLarge)
         ChoiceChips(options=listOf("Staff","Manager"), selected=newStaffRole, onSelect={ newStaffRole=it })
         PrimaryAction(text=if(editingStaffUsername==null) "Create staff account" else "Save account changes", onClick={
          if(!isOwner()) denyPermission("manage user accounts") else {
           val wasEditing=editingStaffUsername!=null
           runCatching {
            if(editingStaffUsername==null) accountStore.create(newStaffName.trim(),newStaffPassword,newStaffRole)
            else accountStore.update(editingStaffUsername!!,newStaffRole,newStaffPassword)
            accountVersion++
            newStaffName="";newStaffPassword="";newStaffRole="Staff";editingStaffUsername=null
           }.onSuccess { toast(if(wasEditing) "User account updated" else "User account created") }.onFailure { toast(it.message ?: "Could not save user account") }
          }
         }, icon=Icons.Rounded.Check, enabled=if(editingStaffUsername==null) newStaffName.trim().length>=3&&newStaffPassword.length>=8 else newStaffPassword.isBlank()||newStaffPassword.length>=8)
         if(editingStaffUsername!=null) SecondaryAction(text="Cancel account edit", onClick={ editingStaffUsername=null;newStaffName="";newStaffPassword="";newStaffRole="Staff" })
        }
        Spacer(Modifier.height(20.dp))
        Text("Accounts on this device", style=MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        accountVersion
        accountStore.loadUsers().forEach { user ->
         RecordCard(modifier=Modifier.padding(vertical=4.dp)) {
          Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
           AvatarBadge(user.username)
           Column(Modifier.weight(1f)) { Text(user.username, style=MaterialTheme.typography.titleMedium) }
           StatusPill(user.role.uppercase(), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary)
           if(isOwner() && user.role!="Owner" && user.username!=loggedInUser?.username) {
            IconButton(onClick={ newStaffName=user.username;newStaffRole=user.role;newStaffPassword="";editingStaffUsername=user.username;lifecycleScope.launch { mainScroll.animateScrollTo(0) } }) { Icon(Icons.Rounded.Edit, contentDescription="Edit account", tint=MaterialTheme.colorScheme.primary) }
            IconButton(onClick={ if(isOwner()) { accountStore.remove(user.username);accountVersion++;toast("Account removed") } else denyPermission("manage user accounts") }) { Icon(Icons.Rounded.Delete, contentDescription="Remove account", tint=MaterialTheme.colorScheme.error) }
           }
          }
         }
        }
        Text("This is device-local account management, not cloud identity or multi-device synchronization.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
       }
       "Settings" -> {
        var shopName by remember { mutableStateOf("Surya Optician") }
        var shopAddress by remember { mutableStateOf("") }
        var shopPhone by remember { mutableStateOf("") }
        var shopGstin by remember { mutableStateOf("") }
        var shopFooter by remember { mutableStateOf("Thank you for your purchase!") }
        LaunchedEffect(Unit) { db.dao().settings()?.let { shopName=it.name;shopAddress=it.address;shopPhone=it.phone;shopGstin=it.gstin;shopFooter=it.footer } }
        ScreenHeader("Settings", "Shop profile, security and data")
        Spacer(Modifier.height(12.dp))
        SectionCard(title="Shop profile", icon=Icons.Rounded.Store, subtitle="Shown on every invoice") {
         AppField(value=shopName, onValueChange={ shopName=it }, label="Shop name")
         AppField(value=shopAddress, onValueChange={ shopAddress=it }, label="Shop address", singleLine=false, minLines=2)
         AppField(value=shopPhone, onValueChange={ shopPhone=it }, label="Shop phone", keyboardType=KeyboardType.Phone)
         AppField(value=shopGstin, onValueChange={ shopGstin=it }, label="GSTIN")
         AppField(value=shopFooter, onValueChange={ shopFooter=it }, label="Invoice footer")
         PrimaryAction(text="Save settings", onClick={ if(!isOwner()) denyPermission("update shop settings") else lifecycleScope.launch { runCatching { require(shopName.isNotBlank()) { "Shop name is required" }; Validation.phoneError(shopPhone)?.let { error(it) }; db.dao().saveSettings(ShopSettings(name=shopName.trim(),address=shopAddress.trim(),phone=shopPhone.trim(),gstin=shopGstin.trim(),footer=shopFooter.trim())) }.onSuccess { toast("Shop settings saved") }.onFailure { toast(it.message ?: "Could not update shop settings") } } }, icon=Icons.Rounded.Check)
        }
        if(isOwner()) {
         Spacer(Modifier.height(12.dp))
         SectionCard(title="App PIN lock", icon=Icons.Rounded.Lock, subtitle="Optional 4–8 digit PIN required when the app opens") {
          Text("If you forget the PIN, clear app data to remove it; there is no online recovery.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
          AppField(value=newPin, onValueChange={ newPin=it.filter(Char::isDigit).take(8) }, label="New PIN (4–8 digits)", keyboardType=KeyboardType.NumberPassword)
          AppField(value=confirmPin, onValueChange={ confirmPin=it.filter(Char::isDigit).take(8) }, label="Confirm PIN", keyboardType=KeyboardType.NumberPassword)
          PrimaryAction(text="Save / change app PIN", onClick={
         if(newPin.length !in 4..8 || newPin != confirmPin) toast("PIN must be 4–8 digits and both entries must match")
         else runCatching {
          val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
          val encodedSalt=java.util.Base64.getEncoder().encodeToString(salt)
          val encodedHash=java.util.Base64.getEncoder().encodeToString(derivePinHash(newPin,salt))
          securityPrefs.edit().putString("pin_salt",encodedSalt).putString("pin_hash",encodedHash).apply()
          newPin="";confirmPin=""
         }.onSuccess { toast("App PIN saved. It will be required next time the app opens.") }.onFailure { toast("Could not save PIN") }
        }, icon=Icons.Rounded.Lock)
          SecondaryAction(text="Disable app PIN", onClick={ securityPrefs.edit().remove("pin_hash").remove("pin_salt").apply(); newPin=""; confirmPin=""; toast("App PIN disabled") })
         }
         Spacer(Modifier.height(12.dp))
         SectionCard(title="Backup & restore", icon=Icons.Rounded.Backup, subtitle="Keep a private copy of all shop data") {
          Text("Export a full local database backup and keep it private. Restore replaces the current local database; only restore a backup made by this app.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
          PrimaryAction(text="Export database backup", onClick={ backupLauncher.launch("surya-optician-backup.db") }, icon=Icons.Rounded.FileDownload)
          SecondaryAction(text="Restore database backup", onClick={ restoreLauncher.launch(arrayOf("application/vnd.sqlite3", "application/octet-stream", "application/x-sqlite3", "*/*")) }, icon=Icons.Rounded.Restore)
         }
        }
        Spacer(Modifier.height(24.dp))
       }
       else -> {
        AppField(value=query, onValueChange={ query=it }, label="Search bill no., customer or phone", icon=Icons.Rounded.Search, onClear={ query="" })
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
         listOf("All" to searchedInvoices.size, "Paid" to searchedInvoices.count { it.duePaise<=0 }, "Due" to searchedInvoices.count { it.duePaise>0 }).forEach { entry ->
          FilterChip(selected=historyFilter==entry.first, onClick={ historyFilter=entry.first }, label={ Text("${entry.first} · ${entry.second}") })
         }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick={ InvoiceCsv.share(this@MainActivity, invoices) }, modifier=Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FileDownload, contentDescription=null, modifier=Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Export matching invoices as CSV") }
        Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
         Row(Modifier.fillMaxWidth().padding(horizontal=8.dp, vertical=4.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
          Text(if(filteredBillHistory.isEmpty()) "No bills" else "Showing ${billHistoryPage * billHistoryPageSize + 1}–${minOf((billHistoryPage + 1) * billHistoryPageSize, filteredBillHistory.size)} of ${filteredBillHistory.size}", style=MaterialTheme.typography.labelMedium)
          Row(verticalAlignment=Alignment.CenterVertically) {
           TextButton(onClick={ billHistoryPage = (billHistoryPage - 1).coerceAtLeast(0) }, enabled=billHistoryPage > 0) { Text("Previous") }
           Text("${billHistoryPage + 1}/${billHistoryPageCount}", style=MaterialTheme.typography.labelMedium)
           TextButton(onClick={ billHistoryPage = (billHistoryPage + 1).coerceAtMost(billHistoryPageCount - 1) }, enabled=billHistoryPage < billHistoryPageCount - 1) { Text("Next") }
          }
         }
        }
        if(invoices.isEmpty()) EmptyState(Icons.Rounded.Receipt, if(query.isBlank() && historyFilter=="All") "No bills yet" else "No bills found", if(query.isBlank() && historyFilter=="All") "Create your first bill to get started." else "Try a different search or filter.")
        Column {
         visibleBillHistory.forEach { invoice ->
          Card(Modifier.fillMaxWidth().padding(vertical=4.dp), shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface), border=BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
           Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
             Column(Modifier.weight(1f)) {
              Text(invoice.number, style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.secondary)
              Text(invoice.customerName, style=MaterialTheme.typography.titleMedium, maxLines=1)
              Text(java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(invoice.createdAt)), style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
             }
             PaymentBadge(invoice.duePaise, invoice.paidPaise)
            }
            Spacer(Modifier.height(10.dp))
            Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.background, modifier=Modifier.fillMaxWidth()) {
             Row(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=9.dp)) {
              Column(Modifier.weight(1f)) { Text("Total", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant); Text(formatRupees(invoice.totalPaise), style=MaterialTheme.typography.titleSmall) }
              Column(Modifier.weight(1f)) { Text("Paid", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant); Text(formatRupees(invoice.paidPaise), style=MaterialTheme.typography.titleSmall, color=Brand.Success) }
              Column(Modifier.weight(1f)) { Text("Due", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant); Text(formatRupees(invoice.duePaise), style=MaterialTheme.typography.titleSmall, color=if(invoice.duePaise>0) Brand.Warning else MaterialTheme.colorScheme.onSurface) }
             }
            }
            if(invoice.duePaise>0) TextButton(onClick={ paymentInvoiceId=invoice.id.toString(); paymentAmount=""; section="Payment" }) { Icon(Icons.Rounded.Payments, contentDescription=null, modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Collect due payment") }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp), modifier=Modifier.horizontalScroll(rememberScrollState())) {
             if(canManageRecords()) TextButton(onClick={ lifecycleScope.launch { runCatching { val items=db.dao().items(invoice.id); billCustomer=invoice.customerName; billCustomerId=invoice.customerId?.toString() ?: ""; billPhone=invoice.phone; val linkedCustomer=invoice.customerId?.let { db.dao().customer(it) }; billCustomerAddress=linkedCustomer?.address.orEmpty(); billCustomerAge=linkedCustomer?.age?.toString() ?: ""; billCustomerEmail=linkedCustomer?.email.orEmpty(); billCustomerSearch=linkedCustomer?.name.orEmpty(); billLines=items.map { BillLine(it.productId,it.description,it.qty,it.ratePaise,it.discountPaise,it.taxPaise) }; billPaid="%.2f".format(invoice.paidPaise/100.0); billPrescriptionId=invoice.prescriptionId?.toString().orEmpty(); billPrescriptionSummary=invoice.prescriptionSummary; billGlassType=invoice.glassType; billDeliveryDate=invoice.deliveryDate; editingInvoiceId=invoice.id; section="New Bill" }.onFailure { toast("Could not load bill for editing") } } }) { Icon(Icons.Rounded.Edit, contentDescription=null, modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit") }
             if(canDeleteRecords()) TextButton(onClick={ invoiceDeleteTarget=invoice }) { Icon(Icons.Rounded.Delete, contentDescription=null, modifier=Modifier.size(18.dp), tint=MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp)); Text("Delete", color=MaterialTheme.colorScheme.error) }
             TextButton(onClick={lifecycleScope.launch{runCatching{val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings(),invoice.customerId?.let { db.dao().customer(it) });val uri=FileProvider.getUriForFile(this@MainActivity,"${packageName}.fileprovider",file);startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{toast("PDF preview failed. Install a PDF viewer.")}}}){ Icon(Icons.Rounded.Description, contentDescription=null, modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("PDF") }
             TextButton(onClick={lifecycleScope.launch{runCatching{val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings(),invoice.customerId?.let { db.dao().customer(it) });printPdf(file,"Invoice ${invoice.number}")}.onFailure{toast("Could not start system print")}}}){ Icon(Icons.Rounded.Print, contentDescription=null, modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Print") }
              TextButton(onClick={lifecycleScope.launch{runCatching{val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings(),invoice.customerId?.let { db.dao().customer(it) });legacyDownloadFile=file;legacyPdfDownloadLauncher.launch("Invoice-${invoice.number}.pdf")}.onFailure{toast(it.message ?: "Invoice download failed")}}}){ Icon(Icons.Rounded.FileDownload, contentDescription=null, modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Download") }
             Button(onClick={lifecycleScope.launch{runCatching{val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings(),invoice.customerId?.let { db.dao().customer(it) });InvoiceSharing.share(this@MainActivity,file,invoice.phone,true)}.onFailure{toast("Invoice share failed")}}}){ Icon(Icons.Rounded.Share, contentDescription=null, modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("WhatsApp") }
            }
            if(invoice.duePaise>0) TextButton(onClick={reminderInvoice=invoice}){ Icon(Icons.Rounded.NotificationsActive, contentDescription=null, modifier=Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Reminder") }
           }
          }
         }
        }
       }
      }
      }
     }
    }
    paymentDeleteTarget?.let { target ->
     AlertDialog(
      onDismissRequest={ paymentDeleteTarget=null },
      title={ Text("Delete payment?") },
      text={ Text("Delete this ₹${"%.2f".format(target.amountPaise/100.0)} ${target.method} transaction? The invoice paid and due totals will be recalculated.") },
      confirmButton={ TextButton(onClick={
       val selected=target;paymentDeleteTarget=null
       if(!canDeleteRecords()) denyPermission("delete payment records")
       else lifecycleScope.launch {
        runCatching { BillingRepository(db).deletePayment(selected.id) }
         .onSuccess { if(editingPaymentId==selected.id) {editingPaymentId=null;paymentAmount="";paymentReference=""};toast("Payment deleted and invoice balance recalculated") }
         .onFailure { toast(it.message ?: "Could not delete payment") }
       }
      }) { Text("Delete payment", color=MaterialTheme.colorScheme.error) } },
      dismissButton={ TextButton(onClick={paymentDeleteTarget=null}) { Text("Cancel") } }
     )
    }
    invoiceDeleteTarget?.let { target ->
     AlertDialog(
      onDismissRequest={ invoiceDeleteTarget=null },
      title={ Text("Delete bill ${target.number}?") },
      text={ Text("This permanently deletes this invoice, its item and payment records, and restores stock for inventory products. This cannot be undone. Export a backup first if you need to retain the accounting record.") },
      confirmButton={ TextButton(onClick={
       val selected=target; invoiceDeleteTarget=null
       if (!canDeleteRecords()) { denyPermission("delete bills") } else lifecycleScope.launch { runCatching { BillingRepository(db).deleteBill(selected.id) }
        .onSuccess { toast("Bill deleted and stock restored"); if(editingInvoiceId==selected.id) { editingInvoiceId=null; billLines=emptyList(); billPrescriptionSummary="" } }
        .onFailure { toast(it.message ?: "Could not delete bill") }
       }
      }) { Text("Delete permanently", color=MaterialTheme.colorScheme.error) } },
      dismissButton={ TextButton(onClick={invoiceDeleteTarget=null}) { Text("Cancel") } }
     )
    }
    if (showBillPreview) {
     val previewTotal = effectiveBillLines.sumOf { it.total }
     AlertDialog(
      onDismissRequest = { showBillPreview = false },
      title = { Text("Bill preview") },
      text = {
       Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState())) {
        Text("SURYA OPTICIAN", style=MaterialTheme.typography.titleMedium)
        Text("Customer: " + billCustomer.ifBlank { customers.firstOrNull { it.id.toString()==billCustomerId }?.name ?: "Not selected" })
        Text("Phone: " + billPhone.ifBlank { customers.firstOrNull { it.id.toString()==billCustomerId }?.phone ?: "—" })
        if (billCustomerAddress.isNotBlank()) Text("Address: $billCustomerAddress")
        if (billCustomerAge.isNotBlank()) Text("Age: $billCustomerAge")
        if (billGlassType.isNotBlank()) Text("Glass type: $billGlassType")
        if (billDeliveryDate.isNotBlank()) Text("Expected delivery: $billDeliveryDate")
        Spacer(Modifier.height(8.dp))
        effectiveBillLines.forEachIndexed { index, line ->
         Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
          Text("${index+1}. ${line.description}", style=MaterialTheme.typography.bodyMedium)
          Text("Qty: ${line.qty}  ·  Rate: ₹${"%.2f".format(line.ratePaise/100.0)}", style=MaterialTheme.typography.bodySmall)
          Text("Discount: ₹${"%.2f".format(line.discountPaise/100.0)}  ·  Tax: ₹${"%.2f".format(line.taxPaise/100.0)}", style=MaterialTheme.typography.bodySmall)
          Text("Amount: ₹${"%.2f".format(line.total/100.0)}", style=MaterialTheme.typography.bodyMedium)
         }
        }
        Divider(Modifier.padding(vertical=8.dp))
        Text("Subtotal: ₹${"%.2f".format(effectiveBillLines.sumOf { it.subtotalPaise }/100.0)}")
        Text("Discount: ₹${"%.2f".format(effectiveBillLines.sumOf { it.discountPaise }/100.0)}")
        Text("Tax: ₹${"%.2f".format(effectiveBillLines.sumOf { it.taxPaise }/100.0)}")
        Text("GRAND TOTAL: ₹${"%.2f".format(previewTotal/100.0)}", style=MaterialTheme.typography.titleMedium)
        val previewPaidPaise = Money.parseRupeesToPaise(billPaid, blankAs=0L) ?: 0L
        Text("Paid: ${formatRupees(previewPaidPaise)}")
        Text("Balance: ${formatRupees((previewTotal-previewPaidPaise).coerceAtLeast(0))}")
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
}
