package in.surya.optician.data
import androidx.room.withTransaction
import java.text.SimpleDateFormat
import java.util.*
data class BillLine(val productId:Long?,val description:String,val qty:Int,val ratePaise:Long,val discountPaise:Long=0,val taxPaise:Long=0){init{require(description.isNotBlank()&&qty>0&&ratePaise>=0&&discountPaise>=0&&taxPaise>=0)};val total get()=(qty*ratePaise-discountPaise+taxPaise).coerceAtLeast(0)}
data class NewBill(val customerId:Long?,val customerName:String,val phone:String,val lines:List<BillLine>,val discountPaise:Long=0,val paidPaise:Long=0,val method:String="Cash")
class BillingRepository(private val db:OpticianDatabase){
 suspend fun createBill(b:NewBill):Long=db.withTransaction{
  require(b.customerName.isNotBlank()&&b.lines.isNotEmpty());require(b.discountPaise>=0&&b.paidPaise>=0)
  val subtotal=b.lines.sumOf{it.qty*it.ratePaise};val discounts=b.lines.sumOf{it.discountPaise}+b.discountPaise;val taxes=b.lines.sumOf{it.taxPaise};val total=(subtotal-discounts+taxes).coerceAtLeast(0)
  require(b.paidPaise<=total){"Payment exceeds total"}
  b.lines.filter{it.productId!=null}.groupBy{it.productId!!}.forEach{(id,lines)->val p=db.dao().product(id)?:error("Product not found");require(p.stock>=lines.sumOf{it.qty}){"Insufficient stock: ${p.name}"}}
  val dao=db.dao();val stamp=SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(Date());var number="SO-$stamp";var n=1
  while(dao.byNumber(number)!=null) number="SO-$stamp-${n++}"
  val id=dao.addInvoice(Invoice(number=number,customerId=b.customerId,customerName=b.customerName.trim(),phone=b.phone,subtotalPaise=subtotal,discountPaise=discounts,taxPaise=taxes,totalPaise=total,paidPaise=b.paidPaise))
  dao.addItems(b.lines.map{InvoiceItem(invoiceId=id,productId=it.productId,description=it.description,qty=it.qty,ratePaise=it.ratePaise,discountPaise=it.discountPaise,taxPaise=it.taxPaise,totalPaise=it.total)})
  b.lines.filter{it.productId!=null}.groupBy{it.productId!!}.forEach{(pid,lines)->val p=dao.product(pid)!!;dao.updateProduct(p.copy(stock=p.stock-lines.sumOf{it.qty}))}
  if(b.paidPaise>0) dao.addPayment(Payment(invoiceId=id,amountPaise=b.paidPaise,method=b.method))
  id
 }
 suspend fun recordPayment(invoiceId:Long,amountPaise:Long,method:String,reference:String="")=db.withTransaction{
  require(amountPaise>0);val dao=db.dao();val invoice=dao.invoice(invoiceId)?:error("Invoice not found");val paid=dao.paid(invoiceId);require(paid+amountPaise<=invoice.totalPaise){"Payment exceeds due"}
  dao.addPayment(Payment(invoiceId=invoiceId,amountPaise=amountPaise,method=method,reference=reference));dao.setPaid(invoiceId,paid+amountPaise)
 }
}
