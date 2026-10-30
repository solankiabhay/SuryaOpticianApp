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
    val prescriptionSummary: String = "",
    val glassType: String = "",
    val deliveryDate: String = "",
    val prescriptionId: Long? = null,
    val customer: CustomerInput? = null
)

class BillingRepository(private val db: OpticianDatabase) {
    /** Saves (or finds) a customer on its own, e.g. so a prescription can be chosen before the bill exists. */
    suspend fun saveCustomer(input: CustomerInput): Customer = db.withTransaction { upsertCustomer(input) }

    /** Must run inside a transaction: creates, matches or updates a customer without creating duplicates. */
    private suspend fun upsertCustomer(input: CustomerInput): Customer {
        val dao = db.dao()
        require(input.age == null || input.age in 0..120) { "Age must be between 0 and 120" }
        val existing = CustomerMatcher.findExisting(dao.allCustomers(), input.id, input.phone, input.name)
        if (input.id != null && existing == null) error("Customer not found")
        if (existing == null) {
            require(input.name.isNotBlank()) { "Customer name is required" }
            val draft = Customer(name = input.name.trim(), phone = input.phone.trim(), address = input.address.trim(), age = input.age, email = input.email.trim())
            return draft.copy(id = dao.addCustomer(draft))
        }
        val updated = existing.copy(
            name = input.name.trim().ifBlank { existing.name },
            phone = input.phone.trim().ifBlank { existing.phone },
            address = input.address.trim().ifBlank { existing.address },
            age = input.age ?: existing.age,
            email = input.email.trim().ifBlank { existing.email }
        )
        if (updated != existing) dao.updateCustomer(updated)
        return updated
    }

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
        val resolvedCustomer = b.customer?.let { upsertCustomer(it) }
        val customerId = resolvedCustomer?.id ?: b.customerId
        customerId?.let { id -> require(dao.customer(id) != null) { "Customer not found" } }
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
                customerId = customerId,
                customerName = (resolvedCustomer?.name ?: b.customerName).trim(),
                phone = (resolvedCustomer?.phone ?: b.phone).trim(),
                subtotalPaise = subtotal,
                discountPaise = discounts,
                taxPaise = taxes,
                totalPaise = total,
                paidPaise = b.paidPaise,
                prescriptionSummary = b.prescriptionSummary.trim(),
                glassType = b.glassType.trim(),
                deliveryDate = b.deliveryDate.trim(),
                prescriptionId = b.prescriptionId
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

    suspend fun updateBill(invoiceId: Long, b: NewBill) = db.withTransaction {
        require(b.customerName.isNotBlank()) { "Customer name is required" }
        require(b.lines.isNotEmpty()) { "Add at least one bill item" }
        require(b.discountPaise >= 0) { "Discount cannot be negative" }
        val dao = db.dao()
        val existing = dao.invoice(invoiceId) ?: error("Invoice not found")
        val paid = dao.paid(invoiceId)
        require(b.paidPaise >= paid) {
            "Amount received cannot be reduced below recorded payments. Use Payments & collections for any adjustment."
        }
        val subtotal = b.lines.fold(0L) { total, line -> Math.addExact(total, line.subtotalPaise) }
        val lineDiscounts = b.lines.fold(0L) { total, line -> Math.addExact(total, line.discountPaise) }
        val discounts = Math.addExact(lineDiscounts, b.discountPaise)
        val taxes = b.lines.fold(0L) { total, line -> Math.addExact(total, line.taxPaise) }
        require(discounts <= subtotal) { "Total discounts cannot exceed subtotal" }
        val total = Math.addExact(Math.subtractExact(subtotal, discounts), taxes)
        require(b.paidPaise <= total) { "Amount received cannot exceed the revised bill total." }
        val resolvedCustomer = b.customer?.let { upsertCustomer(it) }
        val customerId = resolvedCustomer?.id ?: b.customerId
        customerId?.let { require(dao.customer(it) != null) { "Customer not found" } }

        val oldItems = dao.items(invoiceId)
        oldItems.filter { it.productId != null }.groupBy { it.productId!! }.forEach { (id, lines) ->
            val product = dao.product(id) ?: error("Previously billed product is missing: $id")
            dao.updateProduct(product.copy(stock = Math.addExact(product.stock, lines.sumOf { it.qty })))
        }
        val productCosts = mutableMapOf<Long, Long>()
        b.lines.filter { it.productId != null }.groupBy { it.productId!! }.forEach { (id, lines) ->
            val product = dao.product(id) ?: error("Product not found")
            require(product.stock >= lines.sumOf { it.qty }) { "Insufficient stock: ${product.name}" }
            productCosts[id] = product.costPaise
        }
        dao.updateInvoice(existing.copy(
            customerId = customerId,
            customerName = (resolvedCustomer?.name ?: b.customerName).trim(),
            phone = (resolvedCustomer?.phone ?: b.phone).trim(),
            subtotalPaise = subtotal,
            discountPaise = discounts,
            taxPaise = taxes,
            totalPaise = total,
            paidPaise = b.paidPaise,
            prescriptionSummary = b.prescriptionSummary.trim(),
            glassType = b.glassType.trim(),
            deliveryDate = b.deliveryDate.trim(),
            prescriptionId = b.prescriptionId
        ))
        dao.deleteItemsForInvoice(invoiceId)
        dao.addItems(b.lines.map { line ->
            InvoiceItem(invoiceId=invoiceId, productId=line.productId, description=line.description.trim(),
                qty=line.qty, ratePaise=line.ratePaise, discountPaise=line.discountPaise,
                taxPaise=line.taxPaise, totalPaise=line.total,
                costPaise=line.productId?.let { productCosts[it] } ?: 0L)
        })
        b.lines.filter { it.productId != null }.groupBy { it.productId!! }.forEach { (id, lines) ->
            val product = dao.product(id) ?: error("Product not found")
            dao.updateProduct(product.copy(stock = product.stock - lines.sumOf { it.qty }))
        }
        val additionalPayment = b.paidPaise - paid
        if (additionalPayment > 0) {
            dao.addPayment(
                Payment(
                    invoiceId = invoiceId,
                    amountPaise = additionalPayment,
                    method = b.method.trim().ifBlank { "Cash" },
                    reference = "Additional payment recorded while editing bill"
                )
            )
        }
    }

    suspend fun deleteBill(invoiceId: Long) = db.withTransaction {
        val dao = db.dao()
        dao.invoice(invoiceId) ?: error("Invoice not found")
        val items = dao.items(invoiceId)
        items.filter { it.productId != null }.groupBy { it.productId!! }.forEach { (id, lines) ->
            val product = dao.product(id) ?: error("Cannot restore stock: product $id no longer exists")
            dao.updateProduct(product.copy(stock = Math.addExact(product.stock, lines.sumOf { it.qty })))
        }
        dao.deletePaymentsForInvoice(invoiceId)
        dao.deleteItemsForInvoice(invoiceId)
        dao.deleteInvoice(invoiceId)
    }

    suspend fun updatePayment(paymentId: Long, amountPaise: Long, method: String, reference: String = "") = db.withTransaction {
        require(amountPaise > 0) { "Payment must be greater than zero" }
        val dao = db.dao()
        val existing = dao.payment(paymentId) ?: error("Payment record not found")
        val invoice = dao.invoice(existing.invoiceId) ?: error("Invoice not found")
        val paidBefore = dao.paid(existing.invoiceId)
        val revisedPaid = PaymentAccounting.revisedPaidTotal(paidBefore, existing.amountPaise, amountPaise, invoice.totalPaise)
        dao.updatePayment(existing.copy(
            amountPaise = amountPaise,
            method = method.trim().ifBlank { "Cash" },
            reference = reference.trim()
        ))
        dao.setPaid(existing.invoiceId, revisedPaid)
    }

    suspend fun deletePayment(paymentId: Long) = db.withTransaction {
        val dao = db.dao()
        val existing = dao.payment(paymentId) ?: error("Payment record not found")
        val paidBefore = dao.paid(existing.invoiceId)
        val revisedPaid = PaymentAccounting.paidTotalAfterDelete(paidBefore, existing.amountPaise)
        dao.deletePayment(existing)
        dao.setPaid(existing.invoiceId, revisedPaid)
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
