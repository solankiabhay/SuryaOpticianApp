package in.surya.optician.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val address: String = ""
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sku: String = "",
    val pricePaise: Long,
    val stock: Int,
    val lowStock: Int = 3,
    @ColumnInfo(defaultValue = "0") val costPaise: Long = 0
)

@Entity(tableName = "invoices", indices = [Index(value = ["number"], unique = true)])
data class Invoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val customerId: Long?,
    val customerName: String,
    val phone: String = "",
    val subtotalPaise: Long,
    val discountPaise: Long,
    val taxPaise: Long,
    val totalPaise: Long,
    val paidPaise: Long,
    val createdAt: Long = System.currentTimeMillis()
) {
    val duePaise: Long get() = (totalPaise - paidPaise).coerceAtLeast(0)
}

@Entity(tableName = "invoice_items")
data class InvoiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val productId: Long?,
    val description: String,
    val qty: Int,
    val ratePaise: Long,
    val discountPaise: Long = 0,
    val taxPaise: Long = 0,
    val totalPaise: Long,
    val costPaise: Long = 0
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val amountPaise: Long,
    val method: String,
    val reference: String = "",
    val paidAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "prescriptions")
data class Prescription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerId: Long,
    val rightSphere: String = "",
    val rightCylinder: String = "",
    val rightAxis: String = "",
    val rightAdd: String = "",
    val leftSphere: String = "",
    val leftCylinder: String = "",
    val leftAxis: String = "",
    val leftAdd: String = "",
    val pd: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shop_settings")
data class ShopSettings(
    @PrimaryKey val id: Int = 1,
    val name: String = "Surya Optician",
    val address: String = "",
    val phone: String = "",
    val gstin: String = "",
    val footer: String = "Thank you for your purchase!"
)
