package com.surya.optician.data

import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BillLine(
    val productId: Long?,
    val description: String,
    val qty: Int,
    val ratePaise: Long,
    val discountPaise: Long = 0,
    val taxPaise: Long = 0
) {
    init {
        require(description.isNotBlank() && qty > 0 && ratePaise >= 0 && discountPaise >= 0 && taxPaise >= 0)
        require(discountPaise <= BillingMath.lineGross(qty, ratePaise)) {
            "Line discount cannot exceed line subtotal"
        }
    }

    val total: Long
        get() = BillingMath.lineTotal(qty, ratePaise, discountPaise, taxPaise)
}

data class NewBill(
    val customerId: Long?,
    val customerName: String,
    val phone: String,
    val lines: List<BillLine>,
    val discountPaise: Long = 0,
    val paidPaise: Long = 0,
    val method: String = "Cash"
)

class BillingRepository(private val db: OpticianDatabase) {
    suspend fun createBill(b: NewBill): Long = db.withTransaction {
        require(b.customerName.isNotBlank() && b.lines.isNotEmpty()) { "Customer and at least one item are required" }
        require(b.discountPaise >= 0 && b.paidPaise >= 0) { "Discount and payment cannot be negative" }

        val subtotal = BillingMath.addAmounts(b.lines.map { BillingMath.lineGross(it.qty, it.ratePaise) })
        val lineDiscounts = BillingMath.addAmounts(b.lines.map { it.discountPaise })
        val discounts = Math.addExact(lineDiscounts, b.discountPaise)
        val taxes = BillingMath.addAmounts(b.lines.map { it.taxPaise })
        val total = BillingMath.invoiceTotal(subtotal, discounts, taxes)
        require(b.paidPaise <= total) { "Payment exceeds total" }

        b.lines.filter { it.productId != null }.groupBy { it.productId!! }.forEach { (id, lines) ->
            val product = db.dao().product(id) ?: error("Product not found")
            val requested = lines.fold(0) { count, line -> Math.addExact(count, line.qty) }
            require(product.stock >= requested) { "Insufficient stock: ${product.name}" }
        }

        val dao = db.dao()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        var number = "SO-$stamp"
        var suffix = 1
        while (dao.byNumber(number) != null) number = "SO-$stamp-${suffix++}"

        val id = dao.addInvoice(
            Invoice(
                number = number, customerId = b.customerId,
                customerName = b.customerName.trim(), phone = b.phone,
                subtotalPaise = subtotal, discountPaise = discounts, taxPaise = taxes,
                totalPaise = total, paidPaise = b.paidPaise
            )
        )
        dao.addItems(b.lines.map {
            InvoiceItem(
                invoiceId = id, productId = it.productId, description = it.description.trim(),
                qty = it.qty, ratePaise = it.ratePaise, discountPaise = it.discountPaise,
                taxPaise = it.taxPaise, totalPaise = it.total
            )
        })
        b.lines.filter { it.productId != null }.groupBy { it.productId!! }.forEach { (productId, lines) ->
            val product = dao.product(productId) ?: error("Product not found")
            val sold = lines.fold(0) { count, line -> Math.addExact(count, line.qty) }
            dao.updateProduct(product.copy(stock = product.stock - sold))
        }
        if (b.paidPaise > 0) {
            dao.addPayment(Payment(invoiceId = id, amountPaise = b.paidPaise, method = b.method))
        }
        id
    }

    suspend fun recordPayment(invoiceId: Long, amountPaise: Long, method: String, reference: String = "") =
        db.withTransaction {
            require(amountPaise > 0) { "Payment must be greater than zero" }
            val dao = db.dao()
            val invoice = dao.invoice(invoiceId) ?: error("Invoice not found")
            val paid = dao.paid(invoiceId)
            val newPaid = Math.addExact(paid, amountPaise)
            require(newPaid <= invoice.totalPaise) { "Payment exceeds due" }
            dao.addPayment(Payment(invoiceId = invoiceId, amountPaise = amountPaise, method = method, reference = reference))
            dao.setPaid(invoiceId, newPaid)
        }
}
