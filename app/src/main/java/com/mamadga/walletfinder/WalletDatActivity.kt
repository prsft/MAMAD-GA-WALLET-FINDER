package com.mamadga.walletfinder

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.DecimalFormat

class WalletDatActivity : Activity() {
    private val gold = Color.rgb(224, 181, 86)
    private val background = Color.rgb(12, 15, 22)
    private val panel = Color.rgb(25, 30, 40)
    private lateinit var status: TextView
    private lateinit var details: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = background
        window.navigationBarColor = background
        buildScreen()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply { setBackgroundColor(background) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)
        root.addView(text("WALLET.DAT", 29f, gold, true))
        root.addView(text("RECOVERY ASSISTANT", 11f, Color.LTGRAY, true).apply {
            letterSpacing = 0.18f
            setPadding(0, dp(3), 0, dp(24))
        })
        root.addView(card {
            addView(text("بررسی و راهنمای بازیابی", 19f, Color.WHITE, true))
            addView(text("فایل کیف پول Bitcoin Core را فقط روی همین دستگاه بررسی کن. فایل به هیچ سروری ارسال نمی‌شود.", 14f, Color.LTGRAY, false).apply {
                setPadding(0, dp(10), 0, 0)
            })
        })
        val choose = Button(this).apply {
            text = "انتخاب فایل wallet.dat"
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(gold))
            setOnClickListener { pickFile() }
        }
        root.addView(choose, params(top = 20, bottom = 16))
        root.addView(card {
            addView(text("وضعیت فایل", 16f, gold, true))
            status = text("هنوز فایلی انتخاب نشده است.", 14f, Color.WHITE, false)
            status.setPadding(0, dp(12), 0, dp(4))
            addView(status)
            details = text("", 13f, Color.LTGRAY, false)
            addView(details)
        })
        root.addView(card {
            addView(text("نکات مهم", 16f, gold, true))
            addView(text(
                "• این ابزار فایل را تغییر نمی‌دهد.\n• رمزگذاری کیف پول را نمی‌توان دور زد.\n• از فایل اصلی یک کپی پشتیبان نگه دار.\n• رمزهای احتمالی را فقط خودت و به‌صورت آفلاین مدیریت کن.\n• هیچ‌وقت فایل wallet.dat، رمز یا کلید خصوصی را برای دیگران نفرست.",
                13f, Color.LTGRAY, false
            ).apply { setPadding(0, dp(10), 0, 0) })
        })
        root.addView(text("نسخه تشخیصی محلی • Android 11+", 11f, Color.GRAY, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, 71)
    }

    @Deprecated("Deprecated in Android API; retained for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 71 || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val name = queryName(uri) ?: "wallet.dat"
            val size = querySize(uri)
            val header = contentResolver.openInputStream(uri)?.use { input ->
                val bytes = ByteArray(16)
                val count = input.read(bytes)
                if (count <= 0) "خالی یا غیرقابل‌خواندن" else bytes.take(count).joinToString(" ") { "%02X".format(it) }
            } ?: "قابل‌خواندن نیست"
            status.text = "فایل انتخاب شد: " + name
            details.text = "حجم: " + (if (size >= 0) humanSize(size) else "نامشخص") +
                "\nنمونه هدر: " + header +
                "\n\nاین بررسی فقط انتخاب و خوانایی فایل را تأیید می‌کند؛ ساختار Berkeley DB یا صحت رمز را تأیید نمی‌کند."
        } catch (e: Exception) {
            status.text = "خواندن فایل ناموفق بود."
            details.text = e.localizedMessage ?: "خطای دسترسی به فایل"
        }
    }

    private fun queryName(uri: Uri): String? = query(uri) { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    }

    private fun querySize(uri: Uri): Long = query(uri) { c ->
        val i = c.getColumnIndex(OpenableColumns.SIZE)
        if (i >= 0 && c.moveToFirst() && !c.isNull(i)) c.getLong(i) else -1L
    } ?: -1L

    private fun <T> query(uri: Uri, read: (Cursor) -> T): T? =
        contentResolver.query(uri, null, null, null, null)?.use(read)

    private fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return DecimalFormat("#,##0.0").format(kb) + " KB"
        return DecimalFormat("#,##0.00").format(kb / 1024) + " MB"
    }

    private fun card(build: LinearLayout.() -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(panel)
            build()
            layoutParams = params(top = 8, bottom = 8)
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            textDirection = View.TEXT_DIRECTION_LOCALE
        }

    private fun params(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
