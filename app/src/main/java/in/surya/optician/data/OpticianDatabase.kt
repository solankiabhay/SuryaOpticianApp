package `in`.surya.optician.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Dao
interface OpticianDao {
 @Query("SELECT * FROM customers ORDER BY name") fun customers(): Flow<List<Customer>>
 @Query("SELECT * FROM customers WHERE id=:id") suspend fun customer(id: Long): Customer?
 @Query("SELECT * FROM customers ORDER BY name") suspend fun allCustomers(): List<Customer>
 @Insert suspend fun addCustomer(c: Customer): Long
 @Update suspend fun updateCustomer(c: Customer)
 @Delete suspend fun deleteCustomer(c: Customer)
 @Query("SELECT COUNT(*) FROM invoices WHERE customerId=:id") suspend fun customerInvoiceCount(id: Long): Int
 @Query("SELECT COUNT(*) FROM prescriptions WHERE customerId=:id") suspend fun customerPrescriptionCount(id: Long): Int
 @Query("SELECT COUNT(*) FROM invoices WHERE prescriptionId=:id") suspend fun prescriptionInvoiceCount(id: Long): Int
 @Query("SELECT * FROM products ORDER BY name") fun products(): Flow<List<Product>>
 @Query("SELECT * FROM products WHERE id=:id") suspend fun product(id: Long): Product?
 @Insert suspend fun addProduct(p: Product): Long
 @Update suspend fun updateProduct(p: Product)
 @Delete suspend fun deleteProduct(p: Product)
 @Query("SELECT COUNT(*) FROM invoice_items WHERE productId=:id") suspend fun productInvoiceItemCount(id: Long): Int
 @Insert suspend fun addPrescription(p: Prescription): Long
 @Query("SELECT * FROM prescriptions WHERE id=:id") suspend fun prescription(id: Long): Prescription?
 @Update suspend fun updatePrescription(p: Prescription)
 @Delete suspend fun deletePrescription(p: Prescription)
 @Query("SELECT * FROM prescriptions WHERE customerId=:customerId ORDER BY createdAt DESC") fun prescriptions(customerId: Long): Flow<List<Prescription>>
 @Insert suspend fun addInvoice(i: Invoice): Long
 @Update suspend fun updateInvoice(i: Invoice)
 @Query("DELETE FROM invoice_items WHERE invoiceId=:id") suspend fun deleteItemsForInvoice(id: Long)
 @Query("DELETE FROM payments WHERE invoiceId=:id") suspend fun deletePaymentsForInvoice(id: Long)
 @Query("DELETE FROM invoices WHERE id=:id") suspend fun deleteInvoice(id: Long)
 @Insert suspend fun addItems(items: List<InvoiceItem>)
 @Query("SELECT * FROM invoices ORDER BY createdAt DESC") fun invoices(): Flow<List<Invoice>>
 @Query("SELECT * FROM invoices WHERE number LIKE '%'||:q||'%' OR customerName LIKE '%'||:q||'%' OR phone LIKE '%'||:q||'%' ORDER BY createdAt DESC") fun searchInvoices(q: String): Flow<List<Invoice>>
 @Query("SELECT * FROM invoice_items WHERE invoiceId=:id ORDER BY id") suspend fun items(id: Long): List<InvoiceItem>
 @Query("SELECT * FROM invoices WHERE id=:id") suspend fun invoice(id: Long): Invoice?
 @Query("SELECT * FROM invoices WHERE number=:n LIMIT 1") suspend fun byNumber(n: String): Invoice?
 @Insert suspend fun addPayment(p: Payment): Long
 @Query("SELECT * FROM payments WHERE id=:id") suspend fun payment(id: Long): Payment?
 @Update suspend fun updatePayment(p: Payment)
 @Delete suspend fun deletePayment(p: Payment)
 @Query("SELECT COALESCE(SUM(amountPaise),0) FROM payments WHERE invoiceId=:id") suspend fun paid(id: Long): Long
 @Query("UPDATE invoices SET paidPaise=:amount WHERE id=:id") suspend fun setPaid(id: Long, amount: Long)
 @Query("SELECT * FROM payments WHERE invoiceId=:id ORDER BY paidAt DESC") fun payments(id: Long): Flow<List<Payment>>
 @Query("SELECT * FROM shop_settings WHERE id=1") suspend fun settings(): ShopSettings?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSettings(s: ShopSettings)
 @Query("SELECT COUNT(*) FROM invoices") fun invoiceCount(): Flow<Int>
 @Query("SELECT COALESCE(SUM(totalPaise-paidPaise),0) FROM invoices") fun outstanding(): Flow<Long>
 @Query("SELECT * FROM products WHERE stock<=lowStock ORDER BY stock") fun lowStock(): Flow<List<Product>>
 @Query("SELECT COALESCE(SUM((totalPaise-taxPaise)-(costPaise*qty)),0) FROM invoice_items WHERE invoiceId IN (SELECT id FROM invoices WHERE createdAt>=:fromInclusive AND createdAt<:toExclusive)") fun estimatedProfit(fromInclusive: Long, toExclusive: Long): Flow<Long>
}

@Database(entities=[Customer::class,Product::class,Invoice::class,InvoiceItem::class,Payment::class,Prescription::class,ShopSettings::class],version=8,exportSchema=true)
abstract class OpticianDatabase : RoomDatabase() {
 abstract fun dao(): OpticianDao
 companion object {
  private val MIGRATION_1_2 = object : Migration(1, 2) {
   override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE products ADD COLUMN costPaise INTEGER NOT NULL DEFAULT 0")
    db.execSQL("ALTER TABLE invoice_items ADD COLUMN costPaise INTEGER NOT NULL DEFAULT 0")
   }
  }
  private val MIGRATION_2_3 = object : Migration(2, 3) {
   override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE products ADD COLUMN category TEXT NOT NULL DEFAULT ''")
   }
  }
  private val MIGRATION_3_4 = object : Migration(3, 4) {
   override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE customers ADD COLUMN age INTEGER")
   }
  }
  private val MIGRATION_4_5 = object : Migration(4, 5) {
   override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE invoices ADD COLUMN prescriptionSummary TEXT NOT NULL DEFAULT ''")
   }
  }
  private val MIGRATION_5_6 = object : Migration(5, 6) {
   override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE invoices ADD COLUMN glassType TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE invoices ADD COLUMN deliveryDate TEXT NOT NULL DEFAULT ''")
   }
  }
  private val MIGRATION_6_7 = object : Migration(6, 7) {
   override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE customers ADD COLUMN email TEXT NOT NULL DEFAULT ''")
   }
  }
  private val MIGRATION_7_8 = object : Migration(7, 8) {
   override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE invoices ADD COLUMN prescriptionId INTEGER DEFAULT NULL")
   }
  }
  @Volatile private var instance: OpticianDatabase?=null
  fun resetInstance() {
   synchronized(this) {
    instance?.close()
    instance = null
   }
  }
  fun get(context: Context): OpticianDatabase = instance ?: synchronized(this) {
   instance ?: Room.databaseBuilder(context.applicationContext,OpticianDatabase::class.java,"surya-optician.db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
    .build().also { instance=it }
  }
 }
}
