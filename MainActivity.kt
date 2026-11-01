package com.mamadga.walletfinder

import android.app.Activity
import android.os.Bundle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.math.ec.ECPoint
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

class MainActivity : Activity() {
    private lateinit var addressView: TextView
    private lateinit var wifView: TextView
    private lateinit var hexView: TextView
    private val random = SecureRandom()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = "MAMAD GA WALLET FINDER"
            textSize = 23f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 18)
        }
        content.addView(title)
        content.addView(TextView(this).apply {
            text = "Bitcoin key generator · Mainnet"
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        })
        val generate = Button(this).apply {
            text = "ساخت کلید جدید"
            setOnClickListener { generateKey() }
        }
        content.addView(generate, matchWrap())
        addressView = addField(content, "آدرس عمومی بیت‌کوین")
        wifView = addField(content, "کلید خصوصی WIF")
        hexView = addField(content, "کلید خصوصی HEX")
        val warning = TextView(this).apply {
            text = "هشدار امنیتی: هر کلیدی که این برنامه تولید می‌کند را محرمانه نگه دارید. این نسخه ممیزی امنیتی نشده است؛ برای نگهداری مبالغ واقعی از کیف‌پول معتبر و بررسی‌شده استفاده کنید. کلیدها را برای هیچ‌کس ارسال نکنید. این برنامه موجودی آدرس‌ها را جست‌وجو نمی‌کند."
            textSize = 13f
            setPadding(0, 20, 0, 8)
        }
        content.addView(warning)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun addField(parent: LinearLayout, label: String): TextView {
        val heading = TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 18, 0, 5)
        }
        parent.addView(heading)
        val value = TextView(this).apply {
            text = "—"
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFFEFEFEF.toInt())
        }
        parent.addView(value, matchWrap())
        parent.addView(Button(this).apply {
            text = "کپی"
            setOnClickListener {
                val text = value.text.toString()
                if (text != "—") {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
                    Toast.makeText(this@MainActivity, "کپی شد", Toast.LENGTH_SHORT).show()
                }
            }
        })
        return value
    }

    private fun generateKey() {
        try {
            val params = SECNamedCurves.getByName("secp256k1")
            val n = params.n
            var d: BigInteger
            do {
                val bytes = ByteArray(32)
                random.nextBytes(bytes)
                d = BigInteger(1, bytes)
                bytes.fill(0)
            } while (d.signum() == 0 || d >= n)

            val pub: ECPoint = params.g.multiply(d).normalize()
            val pubBytes = pub.getEncoded(true)
            val hash160 = ripemd160(sha256(pubBytes))
            val address = base58Check(byteArrayOf(0x00) + hash160)
            val privateBytes = to32Bytes(d)
            val wif = base58Check(byteArrayOf(0x80) + privateBytes + byteArrayOf(0x01))

            addressView.text = address
            wifView.text = wif
            hexView.text = privateBytes.joinToString("") { "%02x".format(it) }
            privateBytes.fill(0)
        } catch (_: Exception) {
            Toast.makeText(this, "خطا در تولید کلید", Toast.LENGTH_LONG).show()
        }
    }

    private fun to32Bytes(v: BigInteger): ByteArray {
        val raw = v.toByteArray()
        val out = ByteArray(32)
        val start = if (raw.size > 32) raw.size - 32 else 0
        val len = minOf(32, raw.size)
        System.arraycopy(raw, start, out, 32 - len, len)
        raw.fill(0)
        return out
    }

    private fun sha256(input: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(input)

    private fun ripemd160(input: ByteArray): ByteArray {
        val digest = RIPEMD160Digest()
        digest.update(input, 0, input.size)
        val out = ByteArray(20)
        digest.doFinal(out, 0)
        return out
    }

    private fun base58Check(payload: ByteArray): String {
        val checksum = sha256(sha256(payload)).copyOfRange(0, 4)
        val full = payload + checksum
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var num = BigInteger(1, full)
        val base = BigInteger.valueOf(58)
        val result = StringBuilder()
        while (num > BigInteger.ZERO) {
            val divRem = num.divideAndRemainder(base)
            result.append(alphabet[divRem[1].toInt()])
            num = divRem[0]
        }
        for (b in full) {
            if (b.toInt() == 0) result.append('1') else break
        }
        full.fill(0)
        return result.reverse().toString()
    }
}
