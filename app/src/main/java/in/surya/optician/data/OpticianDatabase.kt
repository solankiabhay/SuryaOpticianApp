package in.surya.optician.data
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
@Dao interface OpticianDao {
 @Query("SELECT * FROM customers ORDER BY name") fun customers():Flow<List<Customer>>
 @Insert suspend fun addCustomer(c:Customer):Long
 @Query("SELECT * FROM products ORDER BY name") fun products():Flow<List<Product>>
 @Query("SELECT * FROM products WHERE id=:id") suspend fun product(id:Long):Product?
 @Insert suspend fun addProduct(p:Product):Long
 @Update suspend fun updateProduct(p:Product)
 @Insert suspend fun addInvoice(i:Invoice):Long
 @Insert suspend fun addItems(items:List<InvoiceItem>)
 @Query("SELECT * FROM invoices ORDER BY createdAt DESC") fun invoices():Flow<List<Invoice>>
 @Query("SELECT * FROM invoices WHERE number LIKE '%'||:q||'%' OR customerName LIKE '%'||:q||'%' OR phone LIKE '%'||:q||'%' ORDER BY createdAt DESC") fun searchInvoices(q:String):Flow<List<Invoice>>
 @Query("SELECT * FROM invoice_items WHERE invoiceId=:id ORDER BY id") suspend fun items(id:Long):List<InvoiceItem>
 @Query("SELECT * FROM invoices WHERE id=:id") suspend fun invoice(id:Long):Invoice?
 @Query("SELECT * FROM invoices WHERE number=:n LIMIT 1") suspend fun byNumber(n:String):Invoice?
 @Insert suspend fun addPayment(p:Payment):Long
 @Query("SELECT COALESCE(SUM(amountPaise),0) FROM payments WHERE invoiceId=:id") suspend fun paid(id:Long):Long
 @Query("UPDATE invoices SET paidPaise=:amount WHERE id=:id") suspend fun setPaid(id:Long,amount:Long)
 @Query("SELECT * FROM payments WHERE invoiceId=:id ORDER BY paidAt DESC") fun payments(id:Long):Flow<List<Payment>>
 @Query("SELECT * FROM shop_settings WHERE id=1") suspend fun settings():ShopSettings?
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveSettings(s:ShopSettings)
 @Query("SELECT COUNT(*) FROM invoices") fun invoiceCount():Flow<Int>
 @Query("SELECT COALESCE(SUM(totalPaise-paidPaise),0) FROM invoices") fun outstanding():Flow<Long>
 @Query("SELECT * FROM products WHERE stock<=lowStock ORDER BY stock") fun lowStock():Flow<List<Product>>
}
@Database(entities=[Customer::class,Product::class,Invoice::class,InvoiceItem::class,Payment::class,Prescription::class,ShopSettings::class],version=1,exportSchema=true)
abstract class OpticianDatabase:RoomDatabase(){
 abstract fun dao():OpticianDao
 companion object { @Volatile private var instance:OpticianDatabase?=null
 fun get(context:Context)=instance?: synchronized(this){instance?:Room.databaseBuilder(context.applicationContext,OpticianDatabase::class.java,"surya-optician.db").build().also{instance=it}}
 }
}
