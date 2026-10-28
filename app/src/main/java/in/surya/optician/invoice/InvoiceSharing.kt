package in.surya.optician.invoice
import android.content.*
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
object InvoiceSharing {
 fun normalizeIndianPhone(raw:String):String{var d=raw.filter{it.isDigit()};if(d.startsWith("00"))d=d.drop(2);return when{d.length==10->"91$d";d.length==12&&d.startsWith("91")->d;d.length==11&&d.startsWith("0")->"91"+d.drop(1);else->""}}
 fun share(context:Context,file:File,phone:String,whatsApp:Boolean){
  val uri:Uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
  val intent=Intent(Intent.ACTION_SEND).apply{type="application/pdf";putExtra(Intent.EXTRA_STREAM,uri);putExtra(Intent.EXTRA_TEXT,"Thank you for visiting our optical shop. Please find your bill attached.");clipData=ClipData.newUri(context.contentResolver,"Invoice PDF",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
  if(whatsApp){val n=normalizeIndianPhone(phone);if(n.isNotBlank()){try{intent.setPackage("com.whatsapp");context.startActivity(Intent.createChooser(intent,"Share invoice PDF"));return}catch(_:Exception){Toast.makeText(context,"WhatsApp unavailable; choose another app.",Toast.LENGTH_LONG).show()}}else Toast.makeText(context,"Missing or invalid phone number; choose a sharing app.",Toast.LENGTH_LONG).show()}
  try{context.startActivity(Intent.createChooser(intent,"Share invoice"))}catch(_:Exception){Toast.makeText(context,"No PDF sharing app is available.",Toast.LENGTH_LONG).show()}
 }
}
