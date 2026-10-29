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
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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


@Composable
private fun OpticianLogo(
 modifier: Modifier = Modifier,
 color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF174C70)
) {
 Canvas(modifier=modifier) {
  val stroke=androidx.compose.ui.graphics.drawscope.Stroke(width=size.minDimension*0.075f)
  val lensRadius=size.minDimension*0.22f
  val left=androidx.compose.ui.geometry.Offset(size.width*0.30f,size.height*0.49f)
  val right=androidx.compose.ui.geometry.Offset(size.width*0.70f,size.height*0.49f)
  drawCircle(color=color,radius=lensRadius,center=left,style=stroke)
  drawCircle(color=color,radius=lensRadius,center=right,style=stroke)
  drawLine(color=color,start=androidx.compose.ui.geometry.Offset(size.width*0.43f,size.height*0.46f),end=androidx.compose.ui.geometry.Offset(size.width*0.57f,size.height*0.46f),strokeWidth=size.minDimension*0.075f)
  drawLine(color=color,start=androidx.compose.ui.geometry.Offset(size.width*0.08f,size.height*0.40f),end=androidx.compose.ui.geometry.Offset(size.width*0.19f,size.height*0.43f),strokeWidth=size.minDimension*0.06f)
  drawLine(color=color,start=androidx.compose.ui.geometry.Offset(size.width*0.81f,size.height*0.43f),end=androidx.compose.ui.geometry.Offset(size.width*0.92f,size.height*0.40f),strokeWidth=size.minDimension*0.06f)
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
        check(sqlite.version in 1..6) { "Unsupported database backup version" }
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
    var unlocked by remember { mutableStateOf(securityPrefs.getString("pin_hash", null) == null) }
    var unlockPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("Dashboard") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var billHistoryPage by remember { mutableIntStateOf(0) }
    var billStatusFilter by remember { mutableStateOf("All") }
    var selectedBillId by remember { mutableStateOf<Long?>(null) }
    var legacyDownloadFile by remember { mutableStateOf<File?>(null) }
    var customerSearch by remember { mutableStateOf("") }
    var productSearch by remember { mutableStateOf("") }
    var editingPrescriptionId by remember { mutableStateOf<Long?>(null) }
    var reportFrom by remember { mutableStateOf("") }
    var reportTo by remember { mutableStateOf("") }
    var selectedCustomerId by remember { mutableStateOf<Long?>(null) }
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
    val legacyPdfDownloadLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { destination ->
     val source = legacyDownloadFile
     legacyDownloadFile = null
     if (destination != null && source != null) {
      runCatching {
       contentResolver.openOutputStream(destination)?.use { output -> source.inputStream().use { input -> input.copyTo(output) } } ?: error("Could not open the selected download location")
      }.onSuccess { Toast.makeText(this@MainActivity, "Invoice PDF downloaded", Toast.LENGTH_LONG).show() }.onFailure { Toast.makeText(this@MainActivity, it.message ?: "Invoice download failed", Toast.LENGTH_LONG).show() }
     }
    }

    LaunchedEffect(query, billStatusFilter) { billHistoryPage = 0; selectedBillId = null }
    val billHistoryPageSize = 10
    val filteredBillHistory = invoices.filter { invoice -> when (billStatusFilter) { "Paid" -> invoice.duePaise <= 0; "Due" -> invoice.duePaise > 0; else -> true } }
    val billHistoryPageCount = maxOf(1, (filteredBillHistory.size + billHistoryPageSize - 1) / billHistoryPageSize)
    val visibleBillHistory = filteredBillHistory.drop(billHistoryPage * billHistoryPageSize).take(billHistoryPageSize)
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
        colors=listOf(androidx.compose.ui.graphics.Color(0xFF102C46),androidx.compose.ui.graphics.Color(0xFF174C70),androidx.compose.ui.graphics.Color(0xFFF2F6FA)),
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
        modifier=Modifier.size(88.dp),
        shape=androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
        color=androidx.compose.ui.graphics.Color(0xFFF8FBFF),
        shadowElevation=10.dp
       ) {
        Box(contentAlignment=Alignment.Center) {
         OpticianLogo(
          modifier=Modifier.size(60.dp),
          color=androidx.compose.ui.graphics.Color(0xFF174C70)
         )
        }
       }
       Spacer(Modifier.height(16.dp))
       Text("SURYA", style=MaterialTheme.typography.headlineMedium, color=androidx.compose.ui.graphics.Color.White, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing=3.sp)
       Text("OPTICIAN  •  SHOP MANAGEMENT", style=MaterialTheme.typography.labelMedium, color=androidx.compose.ui.graphics.Color(0xFFD3E5F5), letterSpacing=1.2.sp)
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
         Text(if(needsOwnerSetup) "Set up your shop" else "Welcome back", style=MaterialTheme.typography.headlineSmall, color=androidx.compose.ui.graphics.Color(0xFF152D43), fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
         Text(
          if(needsOwnerSetup) "Create your owner account to keep your shop records protected." else "Sign in to continue to your billing workspace.",
          style=MaterialTheme.typography.bodyMedium,
          color=androidx.compose.ui.graphics.Color(0xFF647587)
         )
         Spacer(Modifier.height(4.dp))
         OutlinedTextField(
          value=authName,
          onValueChange={authName=it;authError=""},
          label={Text(if(needsOwnerSetup) "Owner username" else "Username")},
          leadingIcon={Text("ID", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary)},
          singleLine=true,
          shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
          modifier=Modifier.fillMaxWidth()
         )
         OutlinedTextField(
          value=authPassword,
          onValueChange={authPassword=it;authError=""},
          label={Text("Password")},
          leadingIcon={Text("••", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.primary)},
          visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),
          singleLine=true,
          shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
          modifier=Modifier.fillMaxWidth()
         )
         if(needsOwnerSetup) OutlinedTextField(
          value=authConfirmPassword,
          onValueChange={authConfirmPassword=it;authError=""},
          label={Text("Confirm password")},
          leadingIcon={Text("✓")},
          visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),
          singleLine=true,
          shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
          modifier=Modifier.fillMaxWidth()
         )
         if(authError.isNotBlank()) {
          Surface(
           color=androidx.compose.ui.graphics.Color(0xFFFFEEEE),
           shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
           modifier=Modifier.fillMaxWidth()
          ) { Text(authError, modifier=Modifier.padding(12.dp), color=MaterialTheme.colorScheme.error, style=MaterialTheme.typography.bodySmall) }
         }
         Spacer(Modifier.height(2.dp))
         Button(
          onClick={
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
          },
          modifier=Modifier.fillMaxWidth().height(54.dp),
          shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
          colors=ButtonDefaults.buttonColors(containerColor=androidx.compose.ui.graphics.Color(0xFF174C70))
         ) {
          Text(if(needsOwnerSetup) "Create owner account" else "Sign in securely", style=MaterialTheme.typography.titleMedium)
         }
         Text(
          "Your account stays on this device. Keep regular backups and never share your password.",
          style=MaterialTheme.typography.bodySmall,
          color=androidx.compose.ui.graphics.Color(0xFF738294)
         )
        }
       }
       Spacer(Modifier.height(18.dp))
       Text("CLEAR VISION. SIMPLE BILLING.", style=MaterialTheme.typography.labelSmall, color=androidx.compose.ui.graphics.Color(0xFFD3E5F5), letterSpacing=1.1.sp)
      }
     }
    } else {
    Scaffold(
     containerColor = MaterialTheme.colorScheme.background,
     topBar = {
      TopAppBar(
       title = {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
         Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp), color=MaterialTheme.colorScheme.primary, modifier=Modifier.size(42.dp)) {
          Box(contentAlignment=androidx.compose.ui.Alignment.Center) { OpticianLogo(modifier=Modifier.size(30.dp), color=androidx.compose.ui.graphics.Color.White) }
         }
         Column {
          Text("Surya Optician", style=MaterialTheme.typography.titleLarge)
          Text("SHOP MANAGEMENT", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.primary)
         }
        }
       },
       actions = {
        if (section == "New Bill") {
         IconButton(onClick={
          if (billLines.isNotEmpty()) showBillPreview=true
          else toast("Preview dekhne se pehle bill mein item add karein.")
         }) { Text("👁", style=MaterialTheme.typography.titleLarge) }
        }
        TextButton(onClick={ loggedInUser=null;authName="";authPassword="";section="Dashboard" }) { Text("Sign out") }
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
       val primaryTabs = listOf("Dashboard", "Bills", "New Bill", "Customers")
       val labels = listOf("Home", "Bills", "Create", "Clients")
       val glyphs = listOf("⌂", "▤", "+", "♙")
       primaryTabs.forEachIndexed { index, tab ->
        NavigationBarItem(
         selected=section==tab,
         onClick={ section=tab; showMoreMenu=false },
         icon={ Text(glyphs[index], style=MaterialTheme.typography.titleMedium) },
         label={ Text(labels[index], style=MaterialTheme.typography.labelSmall, maxLines=1) },
         alwaysShowLabel=true
        )
       }
       NavigationBarItem(
        selected=showMoreMenu || section !in primaryTabs,
        onClick={ showMoreMenu=true },
        icon={ Text("☰", style=MaterialTheme.typography.titleMedium) },
        label={ Text("Menu", style=MaterialTheme.typography.labelSmall, maxLines=1) },
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
             Text(when(tab) { "Dashboard" -> "⌂"; "Bills" -> "▤"; "New Bill" -> "+"; "Customers" -> "♙"; "Products" -> "▦"; "Payment" -> "₹"; "Prescription" -> "◎"; "Reports" -> "▥"; "Settings" -> "⚙"; else -> "♙" }, style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.primary)
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
        Text("Good ${java.text.SimpleDateFormat("EEEE, dd MMMM", java.util.Locale.getDefault()).format(java.util.Date())}", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Card(
         Modifier.fillMaxWidth(),
         shape=androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
         colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color(0xFF102F52))
        ) {
         Column(Modifier.padding(22.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
           Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp), color=androidx.compose.ui.graphics.Color.White, modifier=Modifier.size(58.dp)) {
            Box(contentAlignment=androidx.compose.ui.Alignment.Center) {
             OpticianLogo(modifier=Modifier.size(42.dp), color=androidx.compose.ui.graphics.Color(0xFF174C70))
            }
           }
           Column(Modifier.weight(1f)) {
            Text("SURYA OPTICIAN", style=MaterialTheme.typography.titleMedium, color=androidx.compose.ui.graphics.Color.White)
            Text("CLEAR VISION. BETTER LIFE.", style=MaterialTheme.typography.labelSmall, color=androidx.compose.ui.graphics.Color(0xFFBDD8F4))
           }
          }
          Spacer(Modifier.height(2.dp))
          Text("Namaste, ${loggedInUser?.username ?: "there"}!", style=MaterialTheme.typography.headlineSmall, color=androidx.compose.ui.graphics.Color.White)
          Text("Your optical store, all in one place.", style=MaterialTheme.typography.bodyMedium, color=androidx.compose.ui.graphics.Color(0xFFD9E7F6))
          Button(
           onClick={section="New Bill"},
           modifier=Modifier.fillMaxWidth().height(54.dp),
           shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
           colors=ButtonDefaults.buttonColors(containerColor=androidx.compose.ui.graphics.Color.White, contentColor=androidx.compose.ui.graphics.Color(0xFF123A63))
          ) { Text("＋   Create New Bill", style=MaterialTheme.typography.titleMedium) }
         }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
         Card(Modifier.weight(1f), shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
          Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(7.dp)) {
           Surface(shape=androidx.compose.foundation.shape.CircleShape, color=androidx.compose.ui.graphics.Color(0xFFE2F2FC), modifier=Modifier.size(38.dp)) { Box(contentAlignment=androidx.compose.ui.Alignment.Center) { Text("₹", color=androidx.compose.ui.graphics.Color(0xFF2677A8), style=MaterialTheme.typography.titleLarge) } }
           Text("Outstanding", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
           Text("₹${"%.0f".format(due/100.0)}", style=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.primary)
          }
         }
         Card(Modifier.weight(1f), shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
          Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(7.dp)) {
           Surface(shape=androidx.compose.foundation.shape.CircleShape, color=androidx.compose.ui.graphics.Color(0xFFFFE8D6), modifier=Modifier.size(38.dp)) { Box(contentAlignment=androidx.compose.ui.Alignment.Center) { Text("▦", color=androidx.compose.ui.graphics.Color(0xFF9A4D00), style=MaterialTheme.typography.titleLarge) } }
           Text("Low stock alerts", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
           Text("${lowStock.size}", style=MaterialTheme.typography.titleLarge, color=if(lowStock.isEmpty()) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color(0xFFB45309))
          }
         }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
         listOf(Triple("Bills", allInvoices.size, "▤"), Triple("Customers", customers.size, "♙"), Triple("Products", products.size, "◈")).forEach { metric ->
          Card(Modifier.weight(1f).clickable { section=when(metric.first) { "Bills" -> "Bills"; "Customers" -> "Customers"; else -> "Products" } }, shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
           Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(metric.third, style=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.secondary)
            Text(metric.first, style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${metric.second}", style=MaterialTheme.typography.headlineSmall)
           }
          }
         }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
         Text("Quick actions", style=MaterialTheme.typography.titleLarge)
         Text("SHORTCUTS", style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(10.dp)) {
         Card(Modifier.weight(1f).clickable { section="Customers" }, shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
          Text("♙", style=MaterialTheme.typography.headlineMedium, color=MaterialTheme.colorScheme.primary)
          Text("Add customer", style=MaterialTheme.typography.titleSmall)
          Text("Save customer details", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
         } }
         Card(Modifier.weight(1f).clickable { section="Products" }, shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
          Text("◈", style=MaterialTheme.typography.headlineMedium, color=MaterialTheme.colorScheme.primary)
          Text("Manage products", style=MaterialTheme.typography.titleSmall)
          Text("Frames, lenses & stock", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
         } }
        }
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth().clickable { section="Payment" }, shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.secondaryContainer)) {
         Row(Modifier.padding(16.dp), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(12.dp)) {
          Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color=androidx.compose.ui.graphics.Color.White, modifier=Modifier.size(44.dp)) { Box(contentAlignment=androidx.compose.ui.Alignment.Center) { Text("₹", style=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.primary) } }
          Column(Modifier.weight(1f)) { Text("Collect a payment", style=MaterialTheme.typography.titleMedium); Text("Record payment and update dues", style=MaterialTheme.typography.bodySmall) }
          Text("›", style=MaterialTheme.typography.headlineMedium, color=MaterialTheme.colorScheme.primary)
         }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
         Text("Recent bills", style=MaterialTheme.typography.titleLarge)
         TextButton(onClick={section="Bills"}) { Text("View all") }
        }
        if(allInvoices.isEmpty()) {
         Card(Modifier.fillMaxWidth(), shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
          Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(8.dp)) {
           Text("▤", style=MaterialTheme.typography.displaySmall, color=MaterialTheme.colorScheme.secondary)
           Text("Your bills will appear here", style=MaterialTheme.typography.titleMedium)
           Text("Create your first bill to start tracking sales and payments.", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
           OutlinedButton(onClick={section="New Bill"}) { Text("Create first bill") }
          }
         }
        }
        allInvoices.take(5).forEach { invoice ->
         Card(Modifier.fillMaxWidth().padding(vertical=4.dp).clickable { section="Bills" }, shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
          Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement=Arrangement.spacedBy(12.dp), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
           Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.primaryContainer, modifier=Modifier.size(44.dp)) { Box(contentAlignment=androidx.compose.ui.Alignment.Center) { Text("▤", style=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.primary) } }
           Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(invoice.number, style=MaterialTheme.typography.titleSmall)
            Text(invoice.customerName, style=MaterialTheme.typography.bodyMedium)
            Text(if(invoice.duePaise>0) "Payment due" else "Fully paid", style=MaterialTheme.typography.labelSmall, color=if(invoice.duePaise>0) androidx.compose.ui.graphics.Color(0xFFB45309) else androidx.compose.ui.graphics.Color(0xFF16804A))
           }
           Column(horizontalAlignment=androidx.compose.ui.Alignment.End, verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text("₹${"%.2f".format(invoice.totalPaise/100.0)}", style=MaterialTheme.typography.titleMedium)
            Text(if(invoice.duePaise>0) "Due ₹${"%.2f".format(invoice.duePaise/100.0)}" else "Paid", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
           }
          }
         }
        }
       }
       "Customers" -> {
        Text("Customer details", style=MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(customerName, { customerName = it }, label = { Text("Customer name *") }, modifier = Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(customerPhone, { customerPhone = it }, label = { Text("Phone (WhatsApp)") }, modifier = Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(customerAge, { customerAge = it.filter(Char::isDigit).take(3) }, label = { Text("Age (years, optional)") }, modifier = Modifier.fillMaxWidth(), singleLine=true)
        OutlinedTextField(customerAddress, { customerAddress = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(customerEmail, { customerEmail = it }, label = { Text("Email (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine=true)
        Button(onClick = { if(!canManageRecords()) { denyPermission("edit customer records") } else lifecycleScope.launch {
         val ageValidation = validateCustomerAge(customerAge)
         val ageValue = ageValidation.age
         if (customerName.isBlank()) toast("Customer name is required")
         else if (!ageValidation.isValid) toast("Enter a valid age between 0 and 120")
         else if (selectedCustomerId == null) {
          db.dao().addCustomer(Customer(name=customerName.trim(), phone=customerPhone.trim(), address=customerAddress.trim(), age=ageValue, email=customerEmail.trim()))
          customerName=""; customerPhone=""; customerAddress=""; customerAge=""; customerEmail=""; toast("Customer saved")
         } else {
          db.dao().updateCustomer(Customer(id=selectedCustomerId!!, name=customerName.trim(), phone=customerPhone.trim(), address=customerAddress.trim(), age=ageValue, email=customerEmail.trim()))
          selectedCustomerId=null; customerName=""; customerPhone=""; customerAddress=""; customerAge=""; customerEmail=""; toast("Customer updated")
         }
        } }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedCustomerId == null) "Save customer" else "Update customer") }
        if (selectedCustomerId != null) TextButton(onClick={ selectedCustomerId=null; customerName=""; customerPhone=""; customerAddress=""; customerAge=""; customerEmail="" }) { Text("Cancel customer edit") }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(customerSearch, { customerSearch = it }, label={Text("Search customers by name or phone")}, modifier=Modifier.fillMaxWidth(), singleLine=true)
        Column { customers.filter { customerSearch.isBlank() || it.name.contains(customerSearch,true) || it.phone.contains(customerSearch,true) }.forEach { customer ->
         Card(Modifier.fillMaxWidth().padding(vertical=4.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(12.dp)) {
          Text(customer.name, style=MaterialTheme.typography.titleMedium)
          Text(listOfNotNull(customer.phone.takeIf { it.isNotBlank() }, customer.age?.let { "Age $it years" }, customer.address.takeIf { it.isNotBlank() }, customer.email.takeIf { it.isNotBlank() }).joinToString("  ·  ").ifBlank { "No additional details" }, style=MaterialTheme.typography.bodyMedium)
          Row {
           if(canManageRecords()) TextButton(onClick={ selectedCustomerId=customer.id; customerName=customer.name; customerPhone=customer.phone; customerAddress=customer.address; customerAge=customer.age?.toString() ?: ""; customerEmail=customer.email }) { Text("✎  Edit") }
           if(canDeleteRecords()) TextButton(onClick={ if(!canDeleteRecords()) denyPermission("delete customers") else lifecycleScope.launch {
            val hasInvoices=db.dao().customerInvoiceCount(customer.id)>0
            val hasPrescriptions=db.dao().customerPrescriptionCount(customer.id)>0
            if(hasInvoices || hasPrescriptions) toast("Cannot delete: customer has invoice or prescription history")
            else runCatching { db.dao().deleteCustomer(customer) }.onSuccess { toast("Customer deleted") }.onFailure { toast("Could not delete customer") }
           } }) { Text("⌫  Delete") }
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
        Column { products.filter { productSearch.isBlank() || it.name.contains(productSearch,true) || it.sku.contains(productSearch,true) || it.category.contains(productSearch,true) }.forEach { product ->
         Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) { Column(Modifier.padding(8.dp)) {
          Text("#${product.id} · ${product.name} · Sell ₹${"%.2f".format(product.pricePaise/100.0)} · Cost ₹${"%.2f".format(product.costPaise/100.0)} · ${product.category.ifBlank { "Uncategorised" }} · stock ${product.stock}")
          Row {
           if(canManageRecords()) TextButton(onClick={ selectedProductId=product.id; productName=product.name; productSku=product.sku; productPrice="%.2f".format(product.pricePaise/100.0); productCost="%.2f".format(product.costPaise/100.0); productCategory=product.category; productStock=product.stock.toString(); productLowStock=product.lowStock.toString() }) { Text("✎  Edit") }
           if(canDeleteRecords()) TextButton(onClick={ if(!canDeleteRecords()) denyPermission("delete products") else lifecycleScope.launch {
            if(db.dao().productInvoiceItemCount(product.id)>0) toast("Cannot delete: product appears on invoice history")
            else runCatching { db.dao().deleteProduct(product) }.onSuccess { toast("Product deleted") }.onFailure { toast("Could not delete product") }
           } }) { Text("⌫  Delete") }
          }
         } }
        } }
       }
       "New Bill" -> {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
         Column(Modifier.weight(1f)) {
          Text(if(editingInvoiceId==null) "New bill" else "Edit bill", style=MaterialTheme.typography.headlineSmall)
         }
         OutlinedButton(onClick={ showBillPreview=true }, enabled=billLines.isNotEmpty()) { Text("👁 Preview") }
        }
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth(), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
         Column(Modifier.padding(14.dp)) {
          Text(if(billLines.isEmpty()) "BILL SUMMARY" else "${billLines.size} ${if(billLines.size==1) "item" else "items"} added", style=MaterialTheme.typography.labelLarge)
          Spacer(Modifier.height(4.dp))
          Text("₹${"%.2f".format(billLines.sumOf { it.total }/100.0)}", style=MaterialTheme.typography.headlineMedium, color=MaterialTheme.colorScheme.primary)
         }
        }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
         Text("1 · CUSTOMER", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
         OutlinedTextField(billCustomer, { value ->
          val wasSelected=billCustomerId.isNotBlank()
          billCustomer=value; billCustomerId=""; billCustomerSearchMessage=""
          if(wasSelected) { billCustomerAddress=""; billCustomerAge=""; billCustomerEmail=""; billPrescriptionId="" }
         }, label={ Text("Customer name *") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
         OutlinedTextField(billPhone, { value ->
          val wasSelected=billCustomerId.isNotBlank()
          billPhone=value; billCustomerId=""; billCustomerSearchMessage=""
          if(wasSelected) { billCustomerAddress=""; billCustomerAge=""; billCustomerEmail=""; billPrescriptionId="" }
         }, label={ Text("Phone number *") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
         Button(
          onClick={
           val phoneDigits=billPhone.filter(Char::isDigit)
           val byPhone=if(phoneDigits.isNotBlank()) customers.firstOrNull { it.phone.filter(Char::isDigit)==phoneDigits } else null
           val byName=if(billCustomer.isNotBlank()) customers.filter { it.name.equals(billCustomer.trim(), ignoreCase=true) }.singleOrNull() else null
           val match=byPhone ?: byName
           if(billCustomer.isBlank() && phoneDigits.isBlank()) {
            billCustomerSearchMessage="Enter customer name or phone number to search."
            toast(billCustomerSearchMessage)
           } else if(match!=null) {
            billCustomerId=match.id.toString()
            billCustomer=match.name
            billPhone=match.phone
            billCustomerAddress=match.address
            billCustomerAge=match.age?.toString().orEmpty()
            billCustomerEmail=match.email
            billPrescriptionId=""
            billCustomerSearchMessage="Existing customer found. Details filled automatically."
            toast("Customer found. Details filled automatically.")
           } else {
            billCustomerId=""
            billCustomerEmail=""
            billCustomerSearchMessage="Customer not found. Fill the remaining details to add a new customer."
            toast("Customer not found. You can fill the form for a new customer.")
           }
          },
          modifier=Modifier.fillMaxWidth()
         ) { Text("Search Customer") }
         if(billCustomerSearchMessage.isNotBlank()) Text(billCustomerSearchMessage, style=MaterialTheme.typography.bodySmall, color=if(billCustomerId.isNotBlank()) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant)
         OutlinedTextField(billCustomerAddress, { billCustomerAddress=it }, label={ Text("Address (optional)") }, placeholder={ Text("House no., area, city") }, modifier=Modifier.fillMaxWidth())
         OutlinedTextField(billCustomerAge, { billCustomerAge=it }, label={ Text("Age (optional)") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
         OutlinedTextField(billCustomerEmail, { billCustomerEmail=it }, label={ Text("Email (optional)") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
        } }
        Text("2 · ORDER DETAILS", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
        Text("Glass / lens type", style=MaterialTheme.typography.titleMedium)
        OutlinedTextField(billGlassType, { billGlassType=it }, label={ Text("Glass type (e.g. Single Vision, Progressive, Sunglasses)") }, modifier=Modifier.fillMaxWidth())
        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
         listOf("Single Vision","Bifocal","Progressive","Photochromic","Blue Cut","Sunglasses","Frame Only","Contact Lens").forEach { type ->
          TextButton(onClick={billGlassType=type}) { Text(type, style=MaterialTheme.typography.labelSmall) }
         }
        }
        DateInputField(value=billDeliveryDate, onValueChange={ billDeliveryDate=it }, label="Expected delivery date", displayFormat="dd/MM/yyyy", placeholder="e.g. 05/11/2026", supportingText="Optional for walk-in sales; fill for orders awaiting lens fitting.", modifier=Modifier.fillMaxWidth())
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
        Text("3 · ADD ITEMS", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
        OutlinedTextField(billItemSearch, { billItemSearch=it }, label={ Text("Search frame / lens / product") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
        products.filter { billItemSearch.isBlank() || it.name.contains(billItemSearch, true) || it.sku.contains(billItemSearch, true) || it.category.contains(billItemSearch, true) }.take(6).forEach { product -> TextButton(onClick={ billProductId=product.id.toString(); billDescription=product.name; billRate="%.2f".format(product.pricePaise/100.0); billItemSearch=product.name }) { Text("${product.name} · ₹${"%.2f".format(product.pricePaise/100.0)} · stock ${product.stock}") } }
        OutlinedTextField(billDescription, { billDescription=it }, label={ Text("Item / frame / lens description *") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
         OutlinedTextField(billQty, { billQty=it }, label={ Text("Qty") }, modifier=Modifier.weight(0.7f), singleLine=true)
         OutlinedTextField(billRate, { billRate=it }, label={ Text("Rate ₹") }, modifier=Modifier.weight(1f), singleLine=true)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
         OutlinedTextField(billDiscount, { billDiscount=it }, label={ Text("Discount ₹") }, modifier=Modifier.weight(1f), singleLine=true)
         OutlinedTextField(billTax, { billTax=it }, label={ Text("Tax ₹") }, modifier=Modifier.weight(1f), singleLine=true)
        }
        Button(onClick={
         val qty=billQty.toIntOrNull(); val rate=billRate.toDoubleOrNull(); val discount=billDiscount.toDoubleOrNull(); val tax=billTax.toDoubleOrNull(); val pid=billProductId.toLongOrNull()
         if(billDescription.isBlank() || qty==null || qty<=0 || rate==null || rate<0 || discount==null || discount<0 || tax==null || tax<0 || (billProductId.isNotBlank() && pid==null)) toast("Check item description, product ID, quantity and amounts")
         else runCatching { BillLine(pid,billDescription.trim(),qty,(rate*100).toLong(),(discount*100).toLong(),(tax*100).toLong()) }
          .onSuccess { line -> billLines=billLines+line; billProductId=""; billDescription=""; billQty="1"; billRate=""; billDiscount="0"; billTax="0"; toast("Item added to bill") }
          .onFailure { toast(it.message ?: "Invalid item") }
        }, modifier=Modifier.fillMaxWidth()) { Text("Add item to bill") }
        Text("4 · BILL ITEMS (${billLines.size})", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
        Text("Items in cart · ₹${"%.2f".format(billLines.sumOf { it.total }/100.0)}", style=MaterialTheme.typography.titleSmall)
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
        Text("5 · PAYMENT", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
        OutlinedTextField(billPaid, { billPaid=it }, label={ Text("Amount received ₹") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
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
          run {
           val allCustomers = db.dao().customers().first()
           val age = billCustomerAge.toIntOrNull()
           require(billCustomerAge.isBlank() || (age != null && age in 0..120)) { "Age must be between 0 and 120" }
           val phoneDigits = billPhone.filter(Char::isDigit)
           val matchedCustomer = existingCustomer
            ?: if (phoneDigits.isNotBlank()) allCustomers.firstOrNull { it.phone.filter(Char::isDigit) == phoneDigits }
            else allCustomers.filter { it.name.equals(billCustomer.trim(), ignoreCase=true) }.singleOrNull()
           val finalCustomerName = billCustomer.trim().ifBlank { matchedCustomer?.name.orEmpty() }
           require(finalCustomerName.isNotBlank()) { "Customer name is required" }
           val savedCustomerId = if (matchedCustomer != null) {
            db.dao().updateCustomer(Customer(id=matchedCustomer.id, name=finalCustomerName, phone=billPhone.trim().ifBlank { matchedCustomer.phone }, address=billCustomerAddress.trim().ifBlank { matchedCustomer.address }, age=age ?: matchedCustomer.age, email=billCustomerEmail.trim().ifBlank { matchedCustomer.email }))
            matchedCustomer.id
           } else {
            db.dao().addCustomer(Customer(name=finalCustomerName, phone=billPhone.trim(), address=billCustomerAddress.trim(), age=age, email=billCustomerEmail.trim()))
           }
           val finalPhone = billPhone.trim().ifBlank { matchedCustomer?.phone.orEmpty() }
           val draft = NewBill(
            customerId=savedCustomerId, customerName=finalCustomerName, phone=finalPhone,
            lines=billLines, paidPaise=(paid*100).toLong(), method=billPaymentMethod,
            glassType=billGlassType.trim(), deliveryDate=billDeliveryDate.trim(),
            prescriptionSummary=selectedBillPrescription?.let { rx -> listOf(
             "Right eye (OD): SPH ${rx.rightSphere.ifBlank{"—"}}, CYL ${rx.rightCylinder.ifBlank{"—"}}, Axis ${rx.rightAxis.ifBlank{"—"}}, ADD ${rx.rightAdd.ifBlank{"—"}}",
             "Left eye (OS): SPH ${rx.leftSphere.ifBlank{"—"}}, CYL ${rx.leftCylinder.ifBlank{"—"}}, Axis ${rx.leftAxis.ifBlank{"—"}}, ADD ${rx.leftAdd.ifBlank{"—"}}",
             "PD: ${rx.pd.ifBlank{"—"}}", rx.notes.takeIf { it.isNotBlank() }?.let { "Notes: $it" }
            ).filterNotNull().joinToString("\n") } ?: billPrescriptionSummary
           )
           if(editingInvoiceId==null) BillingRepository(db).createBill(draft)
           else { BillingRepository(db).updateBill(editingInvoiceId!!,draft); editingInvoiceId!! }
          }
         }.onSuccess { id -> toast(if(editingInvoiceId==null) "Invoice created: #$id" else "Invoice updated"); section="Bills"; editingInvoiceId=null; billCustomer=""; billCustomerId=""; billPhone=""; billCustomerAddress=""; billCustomerAge=""; billCustomerSearch=""; billLines=emptyList(); billProductId=""; billDescription=""; billQty="1"; billRate=""; billDiscount="0"; billTax="0"; billPaid="0"; billPaymentMethod="Cash"; billPrescriptionId=""; billPrescriptionSummary=""; billGlassType=""; billDeliveryDate="" }
          .onFailure { toast(it.message ?: "Could not create invoice") }
        } }, modifier=Modifier.fillMaxWidth()) { Text(if(editingInvoiceId==null) "Create bill and deduct stock" else "Save bill changes") }
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
           OutlinedTextField(prescriptionCustomerId, { prescriptionCustomerId=it }, label={ Text("Customer ID") }, placeholder={ Text("Enter or select a customer below") }, modifier=Modifier.fillMaxWidth(), singleLine=true)
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
            OutlinedTextField(rightSphere, { rightSphere=it }, label={ Text("SPH") }, placeholder={ Text("-2.00") }, supportingText={ Text("Sphere") }, modifier=Modifier.weight(1f), singleLine=true)
            OutlinedTextField(rightCylinder, { rightCylinder=it }, label={ Text("CYL") }, placeholder={ Text("-0.75") }, supportingText={ Text("Cylinder") }, modifier=Modifier.weight(1f), singleLine=true)
           }
           Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
            OutlinedTextField(rightAxis, { rightAxis=it }, label={ Text("AXIS") }, placeholder={ Text("90") }, supportingText={ Text("0–180°") }, modifier=Modifier.weight(1f), singleLine=true)
            OutlinedTextField(rightAdd, { rightAdd=it }, label={ Text("ADD") }, placeholder={ Text("+1.50") }, supportingText={ Text("Addition") }, modifier=Modifier.weight(1f), singleLine=true)
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
            OutlinedTextField(leftSphere, { leftSphere=it }, label={ Text("SPH") }, placeholder={ Text("-1.50") }, supportingText={ Text("Sphere") }, modifier=Modifier.weight(1f), singleLine=true)
            OutlinedTextField(leftCylinder, { leftCylinder=it }, label={ Text("CYL") }, placeholder={ Text("-0.50") }, supportingText={ Text("Cylinder") }, modifier=Modifier.weight(1f), singleLine=true)
           }
           Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
            OutlinedTextField(leftAxis, { leftAxis=it }, label={ Text("AXIS") }, placeholder={ Text("180") }, supportingText={ Text("0–180°") }, modifier=Modifier.weight(1f), singleLine=true)
            OutlinedTextField(leftAdd, { leftAdd=it }, label={ Text("ADD") }, placeholder={ Text("+1.50") }, supportingText={ Text("Addition") }, modifier=Modifier.weight(1f), singleLine=true)
           }
          }
         }
         Card(Modifier.fillMaxWidth()) {
          Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) {
           Text("2. ADDITIONAL DETAILS", style=MaterialTheme.typography.labelLarge, color=MaterialTheme.colorScheme.primary)
           OutlinedTextField(prescriptionNotes, { prescriptionNotes=it }, label={ Text("Notes / lens recommendation") }, placeholder={ Text("Optional notes") }, modifier=Modifier.fillMaxWidth(), minLines=3)
           Text("Important: + and − signs matter. Copy the values exactly from the prescribing optometrist. Axis must be between 0 and 180 degrees.", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onSurfaceVariant)
          }
         }
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
             if(canDeleteRecords()) TextButton(onClick={ if(!canDeleteRecords()) denyPermission("delete prescriptions") else lifecycleScope.launch { runCatching { db.dao().deletePrescription(rx) }.onSuccess { toast("Prescription deleted") }.onFailure { toast(it.message ?: "Could not delete prescription") } } }) { Text("Delete") }
            }
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
        DateInputField(value=reportFrom, onValueChange={ reportFrom = it }, label="From date", displayFormat="yyyy-MM-dd", placeholder="2026-10-01", modifier=Modifier.fillMaxWidth())
        DateInputField(value=reportTo, onValueChange={ reportTo = it }, label="To date", displayFormat="yyyy-MM-dd", placeholder="2026-10-31", modifier=Modifier.fillMaxWidth())
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
        Text("Bill history", style=MaterialTheme.typography.headlineSmall)
        Text("Search, review, print, share or collect payment for an existing bill.", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Card(
         modifier=Modifier.fillMaxWidth(),
         colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer),
         shape=androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
        ) {
         Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(10.dp)) {
           Surface(shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.primary, modifier=Modifier.size(42.dp)) {
            Box(contentAlignment=Alignment.Center) { Text("⌕", color=MaterialTheme.colorScheme.onPrimary, style=MaterialTheme.typography.headlineSmall) }
           }
           Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text("Find a bill", style=MaterialTheme.typography.titleMedium, color=MaterialTheme.colorScheme.onPrimaryContainer)
            Text("Search by customer name, bill number or phone", style=MaterialTheme.typography.bodySmall, color=MaterialTheme.colorScheme.onPrimaryContainer)
           }
          }
          OutlinedTextField(
           value=query,
           onValueChange={ query=it },
           modifier=Modifier.fillMaxWidth(),
           placeholder={ Text("e.g. Rahul or SO-20261028") },
           leadingIcon={ Text("⌕", style=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.primary) },
           trailingIcon={
            if(query.isNotBlank()) androidx.compose.material3.IconButton(onClick={ query="" }) {
             Text("×", style=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.primary)
            }
           },
           singleLine=true,
           shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
           colors=OutlinedTextFieldDefaults.colors(
            focusedContainerColor=MaterialTheme.colorScheme.surface,
            unfocusedContainerColor=MaterialTheme.colorScheme.surface,
            focusedBorderColor=MaterialTheme.colorScheme.primary,
            unfocusedBorderColor=MaterialTheme.colorScheme.outline
           )
          )
          Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
           Text(if(query.isBlank()) "Latest bills first" else "${invoices.size} matching bill(s)", style=MaterialTheme.typography.labelMedium, color=MaterialTheme.colorScheme.onPrimaryContainer)
           if(query.isNotBlank()) TextButton(onClick={ query="" }) { Text("Clear") }
          }
         }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("All", "Paid", "Due").forEach { filter -> FilterChip(selected=billStatusFilter==filter, onClick={ billStatusFilter=filter }, label={ Text(if(filter=="Due") "Payment due" else filter) }) } }
         Button(onClick={ InvoiceCsv.share(this@MainActivity, filteredBillHistory) }, modifier=Modifier.fillMaxWidth()) { Text("Export matching invoices as CSV") }
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
        Column {
         visibleBillHistory.forEach { invoice ->
          Card(Modifier.fillMaxWidth().padding(vertical=5.dp).pointerInput(invoice.id) { detectTapGestures(onLongPress={ selectedBillId=invoice.id }, onDoubleTap={ selectedBillId=invoice.id }, onTap={ selectedBillId=if(selectedBillId==invoice.id) null else invoice.id }) }, colors=CardDefaults.cardColors(containerColor=if(selectedBillId==invoice.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
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
            if(selectedBillId==invoice.id) {
             Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
             if(canManageRecords()) TextButton(onClick={ lifecycleScope.launch { runCatching { val items=db.dao().items(invoice.id); billCustomer=invoice.customerName; billCustomerId=invoice.customerId?.toString() ?: ""; billPhone=invoice.phone; val linkedCustomer=invoice.customerId?.let { db.dao().customer(it) }; billCustomerAddress=linkedCustomer?.address.orEmpty(); billCustomerAge=linkedCustomer?.age?.toString() ?: ""; billCustomerSearch=linkedCustomer?.name.orEmpty(); billLines=items.map { BillLine(it.productId,it.description,it.qty,it.ratePaise,it.discountPaise,it.taxPaise) }; billPaid="%.2f".format(invoice.paidPaise/100.0); billPrescriptionId=""; billPrescriptionSummary=invoice.prescriptionSummary; billGlassType=invoice.glassType; billDeliveryDate=invoice.deliveryDate; editingInvoiceId=invoice.id; section="New Bill" }.onFailure { toast("Could not load bill for editing") } } }) { Text("Edit") }
             if(canDeleteRecords()) TextButton(onClick={ invoiceDeleteTarget=invoice }) { Text("Delete") }
             TextButton(onClick={lifecycleScope.launch{runCatching{val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings(),invoice.customerId?.let { db.dao().customer(it) });val uri=FileProvider.getUriForFile(this@MainActivity,"${packageName}.fileprovider",file);startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}.onFailure{toast("PDF preview failed. Install a PDF viewer.")}}}){Text("▧  Preview PDF")}
             TextButton(onClick={lifecycleScope.launch{runCatching{val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings(),invoice.customerId?.let { db.dao().customer(it) });printPdf(file,"Invoice ${invoice.number}")}.onFailure{toast("Could not start system print")}}}){Text("⎙  Print")}
             Button(onClick={lifecycleScope.launch{runCatching{val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings(),invoice.customerId?.let { db.dao().customer(it) });InvoiceSharing.share(this@MainActivity,file,invoice.phone,true)}.onFailure{toast("Invoice share failed")}}}){Text("↗  Share PDF")}
             OutlinedButton(onClick={lifecycleScope.launch {
              runCatching {
               val file=InvoicePdf.generate(this@MainActivity,invoice,db.dao().items(invoice.id),db.dao().settings()?:ShopSettings(),invoice.customerId?.let { db.dao().customer(it) })
               if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values=android.content.ContentValues().apply {
                 put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "Invoice-${invoice.number}.pdf")
                 put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                 put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${android.os.Environment.DIRECTORY_DOWNLOADS}/Surya Optician")
                }
                val destination=contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Could not create a file in Downloads")
                try {
                 contentResolver.openOutputStream(destination)?.use { output -> file.inputStream().use { input -> input.copyTo(output) } } ?: error("Could not write the invoice PDF")
                } catch (error: Exception) {
                 contentResolver.delete(destination, null, null)
                 throw error
                }
                toast("PDF saved in Downloads/Surya Optician")
               } else {
                legacyDownloadFile=file
                legacyPdfDownloadLauncher.launch("Invoice-${invoice.number}.pdf")
               }
              }.onFailure{toast(it.message ?: "Invoice download failed")}
             }}) { Text("↓  Download PDF") }
              }
             } else {
             TextButton(onClick={selectedBillId=invoice.id}) { Text("Hold or double-tap for actions  ⋯") }
            }
            if(selectedBillId==invoice.id && invoice.duePaise>0) TextButton(onClick={reminderInvoice=invoice}){Text("Payment reminder")}
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
       if (!canDeleteRecords()) { denyPermission("delete bills") } else lifecycleScope.launch { runCatching { BillingRepository(db).deleteBill(selected.id) }
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
        if (billCustomerAddress.isNotBlank()) Text("Address: $billCustomerAddress")
        if (billCustomerAge.isNotBlank()) Text("Age: $billCustomerAge")
        if (billGlassType.isNotBlank()) Text("Glass type: $billGlassType")
        if (billDeliveryDate.isNotBlank()) Text("Expected delivery: $billDeliveryDate")
        Spacer(Modifier.height(8.dp))
        billLines.forEachIndexed { index, line ->
         Column(Modifier.fillMaxWidth().padding(vertical=4.dp)) {
          Text("${index+1}. ${line.description}", style=MaterialTheme.typography.bodyMedium)
          Text("Qty: ${line.qty}  ·  Rate: ₹${"%.2f".format(line.ratePaise/100.0)}", style=MaterialTheme.typography.bodySmall)
          Text("Discount: ₹${"%.2f".format(line.discountPaise/100.0)}  ·  Tax: ₹${"%.2f".format(line.taxPaise/100.0)}", style=MaterialTheme.typography.bodySmall)
          Text("Amount: ₹${"%.2f".format(line.total/100.0)}", style=MaterialTheme.typography.bodyMedium)
         }
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
}
