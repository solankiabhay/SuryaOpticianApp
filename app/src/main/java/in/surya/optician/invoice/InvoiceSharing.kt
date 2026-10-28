package `in`.surya.optician.invoice

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object InvoiceSharing {
    fun normalizeIndianPhone(raw: String): String {
        var digits = raw.filter { it.isDigit() }
        if (digits.startsWith("00")) digits = digits.drop(2)
        return when {
            digits.length == 10 -> "91$digits"
            digits.length == 12 && digits.startsWith("91") -> digits
            digits.length == 11 && digits.startsWith("0") -> "91" + digits.drop(1)
            else -> ""
        }
    }

    fun share(context: Context, file: File, phone: String, whatsApp: Boolean) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val normalized = normalizeIndianPhone(phone)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Thank you for visiting our optical shop. Please find your bill attached.")
            clipData = ClipData.newUri(context.contentResolver, "Invoice PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (whatsApp && normalized.isNotBlank()) {
            try {
                intent.setPackage("com.whatsapp")
                // WhatsApp may ignore recipient extras on some versions; always verify the chat before sending.
                intent.putExtra("jid", "$normalized@s.whatsapp.net")
                intent.putExtra("phone", normalized)
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                Toast.makeText(context, "Could not open a targeted WhatsApp share. Choose the correct recipient manually.", Toast.LENGTH_LONG).show()
            }
        } else if (whatsApp) {
            Toast.makeText(context, "Customer phone is missing or invalid; choose the recipient manually.", Toast.LENGTH_LONG).show()
        }

        try {
            context.startActivity(Intent.createChooser(intent, "Share invoice PDF"))
        } catch (_: Exception) {
            Toast.makeText(context, "No PDF sharing app is available.", Toast.LENGTH_LONG).show()
        }
    }
}
