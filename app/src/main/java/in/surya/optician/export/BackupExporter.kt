package com.surya.optician.export

import android.content.Context
import com.surya.optician.data.OpticianDao
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Exports the local Room data to a user-shareable JSON file; it does not upload data anywhere. */
object BackupExporter {
    suspend fun export(context: Context, dao: OpticianDao): File {
        val root = JSONObject()
            .put("format", "surya-optician-backup")
            .put("schemaVersion", 1)
            .put("exportedAt", System.currentTimeMillis())

        root.put("customers", JSONArray().apply {
            dao.allCustomers().forEach { item ->
                put(JSONObject().put("id", item.id).put("name", item.name)
                    .put("phone", item.phone).put("address", item.address))
            }
        })
        root.put("products", JSONArray().apply {
            dao.allProducts().forEach { item ->
                put(JSONObject().put("id", item.id).put("name", item.name).put("sku", item.sku)
                    .put("pricePaise", item.pricePaise).put("stock", item.stock).put("lowStock", item.lowStock))
            }
        })
        root.put("invoices", JSONArray().apply {
            dao.allInvoices().forEach { item ->
                put(JSONObject().put("id", item.id).put("number", item.number)
                    .put("customerId", item.customerId ?: JSONObject.NULL).put("customerName", item.customerName)
                    .put("phone", item.phone).put("subtotalPaise", item.subtotalPaise)
                    .put("discountPaise", item.discountPaise).put("taxPaise", item.taxPaise)
                    .put("totalPaise", item.totalPaise).put("paidPaise", item.paidPaise)
                    .put("createdAt", item.createdAt))
            }
        })
        root.put("invoiceItems", JSONArray().apply {
            dao.allInvoiceItems().forEach { item ->
                put(JSONObject().put("id", item.id).put("invoiceId", item.invoiceId)
                    .put("productId", item.productId ?: JSONObject.NULL).put("description", item.description)
                    .put("qty", item.qty).put("ratePaise", item.ratePaise)
                    .put("discountPaise", item.discountPaise).put("taxPaise", item.taxPaise)
                    .put("totalPaise", item.totalPaise))
            }
        })
        root.put("payments", JSONArray().apply {
            dao.allPayments().forEach { item ->
                put(JSONObject().put("id", item.id).put("invoiceId", item.invoiceId)
                    .put("amountPaise", item.amountPaise).put("method", item.method)
                    .put("reference", item.reference).put("paidAt", item.paidAt))
            }
        })
        root.put("prescriptions", JSONArray().apply {
            dao.allPrescriptions().forEach { item ->
                put(JSONObject().put("id", item.id).put("customerId", item.customerId)
                    .put("rightSphere", item.rightSphere).put("rightCylinder", item.rightCylinder)
                    .put("rightAxis", item.rightAxis).put("rightAdd", item.rightAdd)
                    .put("leftSphere", item.leftSphere).put("leftCylinder", item.leftCylinder)
                    .put("leftAxis", item.leftAxis).put("leftAdd", item.leftAdd)
                    .put("pd", item.pd).put("notes", item.notes).put("createdAt", item.createdAt))
            }
        })
        dao.settings()?.let { item ->
            root.put("shopSettings", JSONObject().put("id", item.id).put("name", item.name)
                .put("address", item.address).put("phone", item.phone)
                .put("gstin", item.gstin).put("footer", item.footer))
        }

        val directory = File(context.filesDir, "invoices")
        check(directory.exists() || directory.mkdirs()) { "Could not create backup directory" }
        return File(directory, "surya-backup-${System.currentTimeMillis()}.json").apply {
            writeText(root.toString(2), Charsets.UTF_8)
        }
    }
}
