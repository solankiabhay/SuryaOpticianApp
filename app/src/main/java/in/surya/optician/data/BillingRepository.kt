package `in`.surya.optician.data

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
        require(description.isNotBlank()) { "Item description is required" }
        require(qty > 0) { "Quantity must be greater than zero" }
        require(ratePaise >= 0 && discountPaise >= 0 && taxPaise >= 0) { "Amounts cannot be negative" }
        val gross = Math.multiplyExact(qty.toLong(), ratePaise)
        require(discountPaise <= gross) { "Line discount cannot exceed the line subtotal" }
        Math.addExact(Math.subtractExact(gross, discountPaise), taxPaise)
    }

    val subtotalPaise: Long get() = Math.multiplyExact(qty.toLong(), ratePaise)
    val total: Long get() = Math.addExact(Math.subtractExact(subtotalPaise, discountPaise), taxPaise)
}

data class NewBill(
    val customerId: Long?,
    val customerName: String,
    val phone: String,
    val lines: List<BillLine>,
    val discountPaise: Long = 0,
    val paidPaise: Long = 0,
    val method: String = "Cash",
    val prescriptionSummary: String = ""
)

class BillingRepository(private val db: OpticianDatabase) {
    suspend fun createBill(b: NewBill): Long = db.withTransaction {
        require(b.customerName.isNotBlank()) { "Customer name is required" }
        require(b.lines.isNotEmpty()) { "Add at least one bill item" }
        require(b.discountPaise >= 0 && b.paidPaise >= 0) { "Discount and payment cannot be negative" }

        val subtotal = b.lines.fold(0L) { total, line -> Math.addExact(total, line.subtotalPaise) }
        val lineDiscounts = b.lines.fold(0L) { total, line -> Math.addExact(total, line.discountPaise) }
        val discounts = Math.addExact(lineDiscounts, b.discountPaise)
        val taxes = b.lines.fold(0L) { total, line -> Math.addExact(total, line.taxPaise) }
        require(discounts <= subtotal) { "Total discounts cannot exceed subtotal" }
        val total = Math.addExact(Math.subtractExact(subtotal, discounts), taxes)
        require(b.paidPaise <= total) { "Payment exceeds total" }

        val dao = db.dao()
        b.customerId?.let { id -> require(dao.customer(id) != null) { "Customer not found" } }
        val productCosts = mutableMapOf<Long, Long>()
        b.lines.filter { it.productId != null }.groupBy { it.productId!! }.forEach { (id, lines) ->
            val product = dao.product(id) ?: error("Product not found")
            val requested = lines.sumOf { it.qty }
            require(product.stock >= requested) { "Insufficient stock: ${product.name}" }
            productCosts[id] = product.costPaise
        }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        var number = "SO-$stamp"
        var suffix = 1
        while (dao.byNumber(number) != null) number = "SO-$stamp-${suffix++}"

        val invoiceId = dao.addInvoice(
            Invoice(
                number = number,
                customerId = b.customerId,
                customerName = b.customerName.trim(),
                phone = b.phone.trim(),
                subtotalPaise = subtotal,
                discountPaise = discounts,
                taxPaise = taxes,
                totalPaise = total,
                paidPaise = b.paidPaise,
                prescriptionSummary = b.prescriptionSummary.trim()
            )
        )
        dao.addItems(b.lines.map { line ->
            InvoiceItem(
                invoiceId = invoiceId,
                productId = line.productId,
                description = line.description.trim(),
                qty = line.qty,
                ratePaise = line.ratePaise,
                discountPaise = line.discountPaise,
                taxPaise = line.taxPaise,
                totalPaise = line.total,
                costPaise = line.productId?.let { productCosts[it] } ?: 0L
            )
        })
        b.lines.filter { it.productId != null }.groupBy { it.productId!! }.forEach { (productId, lines) ->
            val product = dao.product(productId) ?: error("Product not found")
            dao.updateProduct(product.copy(stock = product.stock - lines.sumOf { it.qty }))
        }
        if (b.paidPaise > 0) {
            dao.addPayment(Payment(invoiceId = invoiceId, amountPaise = b.paidPaise, method = b.method))
        }
        invoiceId
    }

    suspend fun recordPayment(invoiceId: Long, amountPaise: Long, method: String, reference: String = "") =
        db.withTransaction {
            require(amountPaise > 0) { "Payment must be greater than zero" }
            val dao = db.dao()
            val invoice = dao.invoice(invoiceId) ?: error("Invoice not found")
            val paid = dao.paid(invoiceId)
            require(amountPaise <= invoice.totalPaise - paid) { "Payment exceeds outstanding balance" }
            dao.addPayment(Payment(invoiceId = invoiceId, amountPaise = amountPaise, method = method.trim().ifBlank { "Cash" }, reference = reference.trim()))
            dao.setPaid(invoiceId, Math.addExact(paid, amountPaise))
        }
}
