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
        runCatching {
            check(file.exists() && file.length() > 0L) { "Invoice PDF is empty or missing" }
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Thank you for visiting our optical shop. Please find your bill attached."
                )
                clipData = ClipData.newUri(context.contentResolver, "Invoice PDF", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // Always show Android's Sharesheet. Do not pin the intent to WhatsApp:
            // that bypasses the app chooser and can make the button appear unresponsive
            // when WhatsApp cannot handle the targeted PDF intent.
            context.startActivity(
                Intent.createChooser(sendIntent, "Share invoice PDF")
            )
        }.onFailure { error ->
            Toast.makeText(
                context,
                error.message ?: "Could not open invoice sharing",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
