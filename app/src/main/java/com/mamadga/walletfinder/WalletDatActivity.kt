package com.mamadga.walletfinder

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import java.text.DecimalFormat
import java.util.Locale

class WalletDatActivity : Activity() {
    private val gold = Color.rgb(224,181,86)
    private val bg = Color.rgb(10,13,20)
    private val panel = Color.rgb(25,30,40)
    private lateinit var fileStatus: TextView
    private lateinit var fileDetails: TextView
    private lateinit var words: EditText
    private lateinit var numbers: EditText
    private lateinit var years: EditText
    private lateinit var result: TextView
    private var candidates = emptyList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val scroll = ScrollView(this).apply { setBackgroundColor(bg) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20),dp(24),dp(20),dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        root.addView(txt("MAMAD GA",13f,Color.LTGRAY,true))
        root.addView(txt("WALLET RECOVERY",26f,gold,true))
        root.addView(txt("دستیار آفلاین آماده‌سازی سرنخ‌های بازیابی",14f,Color.LTGRAY,false))

        root.addView(card {
            addView(txt("۱. انتخاب فایل کیف پول",18f,gold,true))
            addView(txt("فایل فقط روی دستگاه خوانده می‌شود و آپلود نمی‌شود.",14f,Color.WHITE,false))
            addView(btn("انتخاب wallet.dat") { pickFile() })
            fileStatus = txt("فایلی انتخاب نشده",14f,Color.WHITE,true)
            fileDetails = txt("",12f,Color.LTGRAY,false)
            addView(fileStatus); addView(fileDetails)
        })
        root.addView(card {
            addView(txt("۲. سرنخ‌های رمز",18f,gold,true))
            addView(txt("واژه‌ها، عددها و سال‌های احتمالی را با فاصله یا ویرگول جدا کن.",13f,Color.LTGRAY,false))
            words = edit("واژه‌ها؛ مثال: ali,mamad")
            numbers = edit("عددها؛ مثال: 123,786")
            years = edit("سال‌ها؛ مثال: 1378,2005")
            addView(words); addView(numbers); addView(years)
            addView(btn("تولید حداکثر ۳۰۰ کاندیدا") { generate() })
        })
        root.addView(card {
            addView(txt("۳. خروجی کاندیداها",18f,gold,true))
            result = txt("هنوز فهرستی ساخته نشده.",13f,Color.WHITE,false)
            addView(result)
            addView(btn("کپی فهرست") {
                if (candidates.isEmpty()) toast("ابتدا کاندیدا بساز")
                else {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("candidates",candidates.joinToString("\n")))
                    toast("کپی شد")
                }
            })
            addView(btn("ذخیره به‌صورت فایل متنی") { saveList() })
        })
        root.addView(card {
            addView(txt("شفافیت قابلیت‌ها",17f,gold,true))
            addView(txt(
                "این نسخه انتخاب و خواندن اولیه فایل، تولید محدود کاندیدا بر اساس سرنخ، کپی و ذخیره محلی را انجام می‌دهد.\n\n" +
                "تجزیه‌گر معتبر Berkeley DB/SQLite و موتور تأیید رمز Bitcoin Core هنوز در این نسخه وجود ندارد؛ بنابراین برنامه رمزها را امتحان نمی‌کند و ادعای بازکردن کیف پول ندارد.",
                13f,Color.LTGRAY,false
            ))
        })
        root.addView(txt("پردازش سرنخ‌ها روی دستگاه • بدون آپلود",11f,Color.GRAY,false))
    }

    private fun generate() {
        val w = split(words.text.toString())
        val n = split(numbers.text.toString())
        val y = split(years.text.toString())
        val base = (w+n+y).distinct().take(30)
        if (base.isEmpty()) { toast("حداقل یک سرنخ وارد کن"); return }
        val out = LinkedHashSet<String>()
        fun add(s:String) { if (s.length in 1..64 && out.size < 300) out.add(s) }
        val suffix = (n+y+listOf("1","12","123","!","@","2024","2025")).distinct()
        for (word in w) {
            add(word); add(word.lowercase(Locale.ROOT)); add(word.uppercase(Locale.ROOT))
            if (word.isNotEmpty()) add(word.replaceFirstChar { it.uppercase(Locale.ROOT) })
            for (s in suffix) {
                add(word+s); add(word+"_"+s); add(word+"@"+s); add(s+word)
                if (out.size >= 300) break
            }
            if (out.size >= 300) break
        }
        for (i in base.indices) for (j in base.indices) {
            if (i != j && out.size < 300) {
                add(base[i]+base[j]); add(base[i]+"_"+base[j])
            }
        }
        candidates = out.toList()
        result.text = "${candidates.size} کاندیدا ساخته شد.\n\n" +
            candidates.take(40).mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n") +
            if (candidates.size > 40) "\n… و ${candidates.size - 40} مورد دیگر" else ""
    }

    private fun split(s:String) = s.split(Regex("[,،;؛\\\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    private fun saveList() {
        if (candidates.isEmpty()) { toast("ابتدا کاندیدا بساز"); return }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
            putExtra(Intent.EXTRA_TITLE,"wallet-password-candidates.txt")
        }
        startActivityForResult(i,72)
    }
    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="*/*" }
        startActivityForResult(i,71)
    }
    @Deprecated("Compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            if (requestCode == 72) {
                contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(candidates.joinToString("\n")) }
                    ?: throw IllegalStateException("نوشتن فایل ناموفق بود")
                toast("فهرست ذخیره شد")
            } else if (requestCode == 71) {
                val name = query(uri) { c ->
                    val k=c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if(k>=0 && c.moveToFirst()) c.getString(k) else null
                } ?: "wallet.dat"
                val size = query(uri) { c ->
                    val k=c.getColumnIndex(OpenableColumns.SIZE)
                    if(k>=0 && c.moveToFirst() && !c.isNull(k)) c.getLong(k) else -1L
                } ?: -1L
                val header = contentResolver.openInputStream(uri)?.use { input ->
                    val b=ByteArray(16); val count=input.read(b)
                    if(count<=0) "خالی" else b.take(count).joinToString(" ") { "%02X".format(it) }
                } ?: "غیرقابل‌خواندن"
                fileStatus.text = "انتخاب شد: $name"
                fileDetails.text = "حجم: ${if (size >= 0) human(size) else "نامشخص"}\nهدر: $header\nفقط خوانایی اولیه بررسی شد."
            }
        } catch(e:Exception) { toast(e.localizedMessage ?: "خطا") }
    }
    private fun <T> query(uri:Uri, block:(Cursor)->T):T? = contentResolver.query(uri,null,null,null,null)?.use(block)
    private fun human(b:Long):String {
        if(b<1024) return "$b B"
        val k=b/1024.0
        return if(k<1024) DecimalFormat("#,##0.0").format(k)+" KB" else DecimalFormat("#,##0.00").format(k/1024)+" MB"
    }
    private fun edit(h:String)=EditText(this).apply {
        hint=h; textSize=14f; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        setPadding(dp(10),dp(8),dp(10),dp(8)); setBackgroundColor(Color.rgb(39,45,57))
    }
    private fun btn(s:String, action:()->Unit)=Button(this).apply {
        text=s; textSize=14f; setTextColor(Color.BLACK)
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(gold))
        setOnClickListener { action() }
    }
    private fun card(block:LinearLayout.()->Unit)=LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; setPadding(dp(15),dp(15),dp(15),dp(15))
        setBackgroundColor(panel); block()
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8); bottomMargin=dp(8) }
    }
    private fun txt(s:String,size:Float,color:Int,bold:Boolean)=TextView(this).apply {
        text=s; textSize=size; setTextColor(color); if(bold) setTypeface(typeface,Typeface.BOLD)
        textDirection=View.TEXT_DIRECTION_LOCALE; setPadding(0,dp(5),0,dp(5))
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()
}
