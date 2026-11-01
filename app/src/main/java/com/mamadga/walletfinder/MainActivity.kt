package com.mamadga.walletfinder

import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.DecimalFormat
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : FragmentActivity() {
    private val gold = 0xFFFFC857.toInt()
    private val black = 0xFF090909.toInt()
    private val panel = 0xFF171717.toInt()
    private lateinit var root: LinearLayout
    private lateinit var store: EncryptedWalletStore
    private val io = Executors.newSingleThreadExecutor()
    private var english = false
    private var vanityStop: AtomicBoolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        store = EncryptedWalletStore(this)
        english = getSharedPreferences("ui", MODE_PRIVATE).getBoolean("english", false)
        home()
    }
    override fun onDestroy() { super.onDestroy(); vanityStop?.set(true); io.shutdownNow() }
    private fun text(fa: String, en: String) = if (english) en else fa
    private fun base(title: String) {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 18, 20, 20); setBackgroundColor(black) }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        label("MAMAD GA  •  WALLET FINDER", 20, gold, true, Gravity.CENTER)
        label(title, 16, -1, true, Gravity.CENTER)
        space(14)
    }
    private fun label(value: String, size: Int = 14, color: Int = -1, bold: Boolean = false, gravity: Int = Gravity.START): TextView {
        val v = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(color); this.gravity = gravity; if (bold) setTypeface(null, 1); setPadding(4, 8, 4, 8) }
        root.addView(v, LinearLayout.LayoutParams(-1, -2)); return v
    }
    private fun button(value: String, action: () -> Unit) {
        val b = Button(this).apply { text = value; setTextColor(black); setBackgroundColor(gold); isAllCaps = false; setOnClickListener { action() } }
        val p = LinearLayout.LayoutParams(-1, -2); p.setMargins(0, 5, 0, 5); root.addView(b, p)
    }
    private fun space(h: Int) { root.addView(View(this), LinearLayout.LayoutParams(1, h)) }
    private fun field(hint: String, multi: Boolean = false): EditText = EditText(this).apply {
        setTextColor(-1); setHintTextColor(0xFFAAAAAA.toInt()); setHint(hint); setSingleLine(!multi)
        if (multi) minLines = 3
        setPadding(14, 10, 14, 10); setBackgroundColor(panel)
        root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8 })
    }
    private fun home() {
        base(text("کیف پول بیت‌کوین • Mainnet", "Bitcoin wallet • Mainnet"))
        label(text("کلیدها روی دستگاه ساخته می‌شوند؛ عبارت بازیابی را هرگز به کسی ندهید.", "Keys are generated on this device. Never share your recovery phrase."), 13, 0xFFCCCCCC.toInt(), false, Gravity.CENTER)
        button(text("＋ ساخت کیف پول جدید", "＋ Create wallet")) { createDialog() }
        button(text("↻ بازیابی با عبارت ۱۲/۲۴ کلمه‌ای", "↻ Recover from 12/24 words")) { recoverDialog() }
        button(text("▣ کیف پول‌های من", "▣ My wallets")) { walletsPage() }
        button(text("⌕ بررسی موجودی آدرس عمومی", "⌕ Check public address balance")) { checkAddressDialog() }
        button(text("✦ تولید Vanity Address", "✦ Vanity address")) { vanityDialog() }
        button(text("⚙ تنظیمات", "⚙ Settings")) { settingsPage() }
        space(12)
        label(text("هشدار: نسخه آلفا است؛ برای نگهداری مبالغ واقعی استفاده نکنید. آدرس‌های موجودی‌دار جست‌وجو نمی‌شوند.",
            "WARNING: Alpha software; do not store significant funds. This app does not hunt for funded addresses."), 12, gold)
    }
    private fun createDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val name = EditText(this).apply { hint = text("نام کیف پول", "Wallet name"); setSingleLine() }; box.addView(name)
        val pass = EditText(this).apply { hint = text("BIP39 passphrase اختیاری", "Optional BIP39 passphrase"); setSingleLine() }; box.addView(pass)
        val words = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("12", "24")) }; box.addView(words)
        val types = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Native SegWit (BIP84)", "Legacy (BIP44)", "Nested SegWit (BIP49)", "Taproot (BIP86)")) }; box.addView(types)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(text("ساخت کیف پول", "Create wallet")).setView(box)
            .setNegativeButton(text("انصراف", "Cancel"), null).setPositiveButton(text("تولید", "Generate")) { _, _ ->
                runCatching {
                    val type = when (types.selectedItemPosition) {
                        1 -> WalletCore.AddressType.LEGACY
                        2 -> WalletCore.AddressType.NESTED_SEGWIT
                        3 -> WalletCore.AddressType.TAPROOT
                        else -> WalletCore.AddressType.NATIVE_SEGWIT
                    }
                    val created = WalletCore.create(if (words.selectedItemPosition == 0) 12 else 24, pass.text.toString(), type)
                    val record = EncryptedWalletStore.Record(UUID.randomUUID().toString(), name.text.toString().ifBlank { "Bitcoin wallet" },
                        created.mnemonic, pass.text.toString(), created.address, created.path, type.name)
                    store.save(record); showNewPhrase(record)
                }.onFailure { alert(text("خطا در ساخت کیف پول: ", "Wallet creation failed: ") + (it.message ?: "unknown")) }
            }.show()
    }
    private fun recoverDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0) }
        val name = EditText(this).apply { hint = text("نام کیف پول", "Wallet name"); setSingleLine() }; box.addView(name)
        val phrase = EditText(this).apply { hint = text("عبارت بازیابی را وارد کنید", "Enter recovery phrase"); minLines = 3 }; box.addView(phrase)
        val pass = EditText(this).apply { hint = text("BIP39 passphrase (در صورت وجود)", "BIP39 passphrase, if used"); setSingleLine() }; box.addView(pass)
        val types = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Native SegWit (BIP84)", "Legacy (BIP44)")) }; box.addView(types)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(text("بازیابی کیف پول", "Recover wallet")).setView(box)
            .setNegativeButton(text("انصراف", "Cancel"), null).setPositiveButton(text("اعتبارسنجی و بازیابی", "Validate & recover")) { _, _ ->
                runCatching {
                    val type = if (types.selectedItemPosition == 0) WalletCore.AddressType.NATIVE_SEGWIT else WalletCore.AddressType.LEGACY
                    val result = WalletCore.derive(phrase.text.toString(), pass.text.toString(), type)
                    val record = EncryptedWalletStore.Record(UUID.randomUUID().toString(), name.text.toString().ifBlank { "Recovered wallet" },
                        result.mnemonic, pass.text.toString(), result.address, result.path, type.name)
                    store.save(record); showWallet(record)
                }.onFailure { alert(text("عبارت بازیابی معتبر نیست یا passphrase اشتباه است: ", "Invalid recovery phrase or passphrase: ") + (it.message ?: "unknown")) }
            }.show()
    }
    private fun showNewPhrase(r: EncryptedWalletStore.Record) {
        val message = text("عبارت بازیابی را روی کاغذ و به‌ترتیب یادداشت کنید. هرگز آن را برای کسی نفرستید. این عبارت تنها راه بازیابی است:\n\n",
            "Write these recovery words down offline and in order. Never share them. They are the only recovery method:\n\n") + r.mnemonic + "\n\n" + r.address
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(text("پشتیبان بازیابی", "Recovery backup")).setMessage(message)
            .setCancelable(false).setPositiveButton(text("یادداشت کردم", "I have backed it up")) { _, _ -> showWallet(r) }.show()
    }
    private fun walletsPage() {
        base(text("کیف پول‌های ذخیره‌شده", "Saved wallets"))
        val records = runCatching { store.all() }.getOrElse { alert(it.message ?: "Storage error"); emptyList() }
        if (records.isEmpty()) label(text("هنوز کیف پولی ذخیره نشده است.", "No wallets saved yet."), 14, 0xFFBBBBBB.toInt())
        records.forEach { r ->
            button("${r.name}\n${r.address.take(23)}…") { showWallet(r) }
            button(text("حذف ${r.name}", "Delete ${r.name}")) {
                androidx.appcompat.app.AlertDialog.Builder(this).setMessage(text("این کیف پول از دستگاه حذف شود؟", "Delete this wallet from this device?"))
                    .setNegativeButton(text("خیر", "No"), null).setPositiveButton(text("حذف", "Delete")) { _, _ -> store.delete(r.id); walletsPage() }.show()
            }
        }
        button(text("بازگشت", "Back")) { home() }
    }
    private fun showWallet(r: EncryptedWalletStore.Record) {
        base(r.name)
        label(text("موجودی", "Balance"), 13, gold, true)
        val balance = label(text("برای دریافت موجودی، بررسی را بزنید.", "Tap refresh to load balance."), 18, -1, true)
        label(text("آدرس دریافت", "Receive address"), 13, gold, true)
        label(r.address, 13, -1)
        label("${r.type}  •  ${r.path}", 11, 0xFFAAAAAA.toInt())
        button(text("کپی آدرس", "Copy address")) { copy(r.address); alert(text("آدرس کپی شد.", "Address copied.")) }
        button(text("نمایش QR", "Show QR")) { showQr(r.address) }
        button(text("اشتراک‌گذاری آدرس عمومی", "Share public address")) {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, r.address)
            startActivity(android.content.Intent.createChooser(i, text("اشتراک آدرس", "Share address")))
        }
        button(text("به‌روزرسانی موجودی و تراکنش‌ها", "Refresh balance & transactions")) {
            balance.text = text("در حال دریافت اطلاعات…", "Loading…")
            io.execute {
                val result = runCatching { fetchAddressData(r.address) }
                runOnUiThread {
                    result.onSuccess { d ->
                        balance.text = "${formatBtc(d.confirmed)} BTC  (${d.confirmed} sat)\n" + text("تأییدنشده: ", "Unconfirmed: ") + formatBtc(d.unconfirmed) + " BTC"
                        showTransactions(d.txs)
                    }.onFailure { balance.text = text("خطا در دریافت اطلاعات: ", "Network error: ") + (it.message ?: "unknown") }
                }
            }
        }
        button(text("نمایش عبارت بازیابی (احراز هویت دستگاه)", "Reveal recovery phrase (device authentication)")) { authenticate { alert(r.mnemonic) } }
        button(text("نمایش کلید خصوصی WIF (احراز هویت دستگاه)", "Reveal private WIF (device authentication)")) {
            authenticate {
                runCatching { WalletCore.privateKeyWif(r.mnemonic, r.passphrase, WalletCore.AddressType.valueOf(r.type)) }
                    .onSuccess { wif ->
                        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(text("کلید خصوصی — محرمانه", "Private key — secret"))
                            .setMessage(wif + "\n\n" + text("هرگز این کلید را به اشتراک نگذارید.", "Never share this key."))
                            .setNegativeButton(text("بستن", "Close"), null)
                            .setPositiveButton(text("کپی با مسئولیت خودم", "Copy anyway")) { _, _ -> copy(wif); alert(text("کلید کپی شد؛ کلیپ‌بورد را پاک کنید.", "Copied. Clear your clipboard.")) }.show()
                    }.onFailure { alert(it.message ?: "Key export failed") }
            }
        }
        button(text("حذف کیف پول", "Delete wallet")) { store.delete(r.id); walletsPage() }
        button(text("بازگشت", "Back")) { walletsPage() }
    }
    private fun showTransactions(txs: List<String>) {
        val body = if (txs.isEmpty()) text("تراکنشی یافت نشد.", "No transactions returned.") else txs.joinToString("\n\n")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(text("تراکنش‌های اخیر (حداکثر ۲۵ مورد)", "Recent transactions (up to 25)"))
            .setMessage(body).setPositiveButton("OK", null).show()
    }
    private data class AddressData(val confirmed: Long, val unconfirmed: Long, val txs: List<String>)
    private fun fetchAddressData(address: String): AddressData {
        require(WalletCore.validateMainnetAddress(address)) { "Invalid Bitcoin Mainnet address" }
        val encoded = URLEncoder.encode(address, "UTF-8")
        val stats = getJson("https://blockstream.info/api/address/$encoded")
        fun balance(o: JSONObject): Long = o.optLong("funded_txo_sum") - o.optLong("spent_txo_sum")
        val confirmed = balance(stats.getJSONObject("chain_stats"))
        val unconfirmed = balance(stats.getJSONObject("mempool_stats"))
        val txArray = getArray("https://blockstream.info/api/address/$encoded/txs")
        val txs = (0 until minOf(txArray.length(), 25)).map { i ->
            val tx = txArray.getJSONObject(i); val status = tx.optJSONObject("status") ?: JSONObject()
            val confirmedTx = status.optBoolean("confirmed", false)
            val time = if (confirmedTx) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(status.optLong("block_time") * 1000)) else text("در انتظار تأیید", "Unconfirmed")
            "${tx.optString("txid")}\n$time"
        }
        return AddressData(confirmed, unconfirmed, txs)
    }
    private fun getJson(url: String): JSONObject = JSONObject(getText(url))
    private fun getArray(url: String): org.json.JSONArray = org.json.JSONArray(getText(url))
    private fun getText(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "GET"; c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/json")
        return try { if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}"); c.inputStream.bufferedReader().use { it.readText() } }
        finally { c.disconnect() }
    }
    private fun checkAddressDialog() {
        val input = EditText(this).apply { hint = "bc1… / 1… / 3…"; setTextColor(-1) }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(text("بررسی آدرس عمومی بیت‌کوین", "Check public Bitcoin address"))
            .setView(input).setNegativeButton(text("انصراف", "Cancel"), null).setPositiveButton(text("بررسی", "Check")) { _, _ ->
                val address = input.text.toString().trim()
                if (!WalletCore.validateMainnetAddress(address)) { alert(text("آدرس Mainnet معتبر نیست.", "Invalid Bitcoin Mainnet address.")); return@setPositiveButton }
                base(text("نتیجه بررسی آدرس", "Address lookup")); label(address, 12, gold)
                val result = label(text("در حال دریافت…", "Loading…"), 16)
                io.execute { val data = runCatching { fetchAddressData(address) }; runOnUiThread {
                    data.onSuccess { d -> result.text = "${formatBtc(d.confirmed)} BTC confirmed\n${formatBtc(d.unconfirmed)} BTC unconfirmed\n\n${d.txs.joinToString("\n\n")}" }
                        .onFailure { result.text = it.message ?: "Network error" }
                } }
            }.show()
    }
    private fun vanityDialog() {
        val input = EditText(this).apply { hint = "bc1q + prefix (e.g. bc1qabc)"; setTextColor(-1) }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(text("جست‌وجوی Vanity محلی", "Local vanity search"))
            .setMessage(text("فقط پیشوندهای آدرس Native SegWit پشتیبانی می‌شوند. ممکن است زمان‌بر و پرمصرف باشد. هیچ آدرس دارای موجودی جست‌وجو نمی‌شود.",
                "Only Native SegWit prefixes are supported. This can be slow and battery-intensive. No funded-address hunting is performed."))
            .setView(input).setNegativeButton(text("انصراف", "Cancel"), null).setPositiveButton(text("شروع", "Start")) { _, _ ->
                val prefix = input.text.toString().trim().lowercase()
                if (!prefix.startsWith("bc1q") || prefix.length !in 5..10) { alert(text("پیشوند باید با bc1q شروع شود و حداکثر ۱۰ نویسه باشد.", "Prefix must start with bc1q and be at most 10 characters.")); return@setPositiveButton }
                startVanity(prefix)
            }.show()
    }
    private fun startVanity(prefix: String) {
        val status = TextView(this).apply { setTextColor(-1); textSize = 15f; text = text("در حال جست‌وجو…", "Searching…") }
        val stop = AtomicBoolean(false); vanityStop = stop
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setTitle(text("تولید Vanity Address", "Vanity search"))
            .setView(status).setNegativeButton(text("توقف", "Stop")) { _, _ -> stop.set(true) }.create()
        dialog.show()
        io.execute {
            var tries = 0L
            while (!stop.get()) {
                tries++
                val made = runCatching { WalletCore.create(12, "", WalletCore.AddressType.NATIVE_SEGWIT) }.getOrNull() ?: continue
                if (made.address.startsWith(prefix)) {
                    stop.set(true)
                    val rec = EncryptedWalletStore.Record(UUID.randomUUID().toString(), "Vanity $prefix", made.mnemonic, "", made.address, made.path, WalletCore.AddressType.NATIVE_SEGWIT.name)
                    runCatching { store.save(rec) }
                    runOnUiThread { if (!isFinishing) { dialog.dismiss(); showNewPhrase(rec) } }
                    break
                }
                if (tries % 5L == 0L) runOnUiThread { if (dialog.isShowing) status.text = "${text("تعداد تلاش: ", "Attempts: ")}$tries\n${text("برای توقف، دکمه توقف را بزنید.", "Press Stop to cancel.")}" }
            }
            runOnUiThread { if (dialog.isShowing && stop.get()) status.text = text("جست‌وجو متوقف شد.", "Search stopped.") }
        }
    }
    private fun settingsPage() {
        base(text("تنظیمات", "Settings"))
        button(if (english) "زبان: English (switch to فارسی)" else "زبان: فارسی (تغییر به English)") {
            english = !english; getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("english", english).apply(); settingsPage()
        }
        label(text("شبکه: Bitcoin Mainnet فقط", "Network: Bitcoin Mainnet only"), 14, gold)
        label(text("ذخیره‌سازی: AES-256-GCM با کلید Android Keystore. پشتیبان‌گیری ابری غیرفعال است.",
            "Storage: AES-256-GCM with Android Keystore key. Android cloud backup is disabled."), 13)
        label(text("قفل صفحه: نمایش امن با FLAG_SECURE. مشاهده عبارت و کلید خصوصی نیازمند احراز هویت بیومتریک/قفل دستگاه است.",
            "Screen security: FLAG_SECURE. Revealing seed/private key requires device biometric or screen-lock authentication."), 13)
        label(text("این نسخه فاقد PIN مستقل، خروجی پشتیبان رمزگذاری‌شده، اسکن QR، ارسال تراکنش، Taproot و Nested SegWit است.",
            "This alpha does not include app PIN, encrypted backup export, QR scanning, transaction signing/broadcast, Taproot or Nested SegWit."), 13, gold)
        label("MAMAD GA WALLET FINDER • 0.1.0-alpha", 12, 0xFFAAAAAA.toInt())
        button(text("بازگشت", "Back")) { home() }
    }
    private fun authenticate(onSuccess: () -> Unit) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            alert(text("برای مشاهده اطلاعات محرمانه، قفل صفحه امن یا بیومتریک دستگاه را فعال کنید.",
                "Enable a secure screen lock or strong biometrics to reveal secrets.")); return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            override fun onAuthenticationError(code: Int, errString: CharSequence) { /* no secret shown */ }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(text("احراز هویت برای نمایش کلید", "Authenticate to reveal secret"))
            .setSubtitle(text("از بیومتریک یا قفل دستگاه استفاده کنید", "Use biometrics or device credential"))
            .setAllowedAuthenticators(authenticators).build()
        prompt.authenticate(info)
    }
    private fun showQr(address: String) {
        runCatching {
            val matrix = QRCodeWriter().encode(address, BarcodeFormat.QR_CODE, 720, 720)
            val bmp = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888)
            for (x in 0 until 720) for (y in 0 until 720) bmp.setPixel(x, y, if (matrix[x, y]) 0xFF000000.toInt() else -1)
            val image = ImageView(this).apply { setImageBitmap(bmp); setPadding(20, 20, 20, 20) }
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle(text("QR دریافت بیت‌کوین", "Bitcoin receive QR"))
                .setView(image).setPositiveButton("OK", null).show()
        }.onFailure { alert(it.message ?: "QR error") }
    }
    private fun formatBtc(sat: Long) = DecimalFormat("0.00000000").format(sat / 100_000_000.0)
    private fun copy(s: String) { val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager; cm.setPrimaryClip(android.content.ClipData.newPlainText("public", s)) }
    private fun alert(s: String) { androidx.appcompat.app.AlertDialog.Builder(this).setMessage(s).setPositiveButton("OK", null).show() }
}
