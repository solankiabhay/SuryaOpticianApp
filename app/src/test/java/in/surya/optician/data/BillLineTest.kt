package in.surya.optician.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BillLineTest {
 @Test fun totalIncludesQuantityDiscountAndTax() {
  val line = BillLine(productId=7, description="Lens", qty=2, ratePaise=12500, discountPaise=1000, taxPaise=500)
  assertEquals(24000L, line.total)
 }
 @Test fun totalNeverBecomesNegative() {
  val line = BillLine(productId=null, description="Adjustment", qty=1, ratePaise=100, discountPaise=500)
  assertEquals(0L, line.total)
 }
 @Test(expected=IllegalArgumentException::class)
 fun rejectsZeroQuantity() {
  BillLine(productId=null, description="Frame", qty=0, ratePaise=100)
 }
 @Test(expected=IllegalArgumentException::class)
 fun rejectsNegativeRate() {
  BillLine(productId=null, description="Frame", qty=1, ratePaise=-1)
 }
}
