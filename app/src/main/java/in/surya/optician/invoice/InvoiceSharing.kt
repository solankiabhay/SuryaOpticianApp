package com.surya.optician.invoice

import android.content.*
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object InvoiceSharing {
    fun normalizeIndianPhone(raw: String): String = IndianPhoneNumber.normalize(raw)

    fun share(context: Context, file: File, phone: String, whatsApp: Boolean) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Thank you for visiting our optical shop. Please find your bill attached.")
            clipData = ClipData.newUri(context.contentResolver, "Invoice PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (whatsApp) {
            val normalized = normalizeIndianPhone(phone)
            if (normalized.isBlank()) {
                Toast.makeText(context, "Missing or invalid phone number; choose a sharing app.", Toast.LENGTH_LONG).show()
            } else {
                // Android's PDF share intent cannot guarantee recipient preselection in WhatsApp.
                Toast.makeText(context, "Review the recipient in WhatsApp before sending the PDF.", Toast.LENGTH_LONG).show()
                intent.setPackage("com.whatsapp")
                try {
                    context.startActivity(Intent.createChooser(intent, "Share invoice PDF"))
                    return
                } catch (_: Exception) {
                    Toast.makeText(context, "WhatsApp unavailable; choose another app.", Toast.LENGTH_LONG).show()
                }
            }
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Share invoice"))
        } catch (_: Exception) {
            Toast.makeText(context, "No PDF sharing app is available.", Toast.LENGTH_LONG).show()
        }
    }
}
