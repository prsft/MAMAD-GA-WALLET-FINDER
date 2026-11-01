package com.mamadga.walletfinder

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores wallet records as AES-256-GCM ciphertext in app-private internal storage. */
class EncryptedWalletStore(private val context: Context) {
    data class Record(val id: String, val name: String, val mnemonic: String, val passphrase: String,
                     val address: String, val path: String, val type: String)
    private val file = File(context.filesDir, "wallets.v1.enc")
    private val alias = "mamad_ga_wallet_store_v1"

    @Synchronized fun all(): List<Record> {
        if (!file.exists()) return emptyList()
        val raw = file.readBytes()
        require(raw.size > 28) { "Encrypted wallet file is damaged" }
        val iv = raw.copyOfRange(0, 12)
        val encrypted = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(encrypted)
        return try {
            val arr = JSONArray(String(plain, Charsets.UTF_8))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Record(o.getString("id"), o.getString("name"), o.getString("mnemonic"),
                    o.optString("passphrase", ""), o.getString("address"), o.getString("path"), o.getString("type"))
            }
        } finally { plain.fill(0); raw.fill(0) }
    }
    @Synchronized fun save(record: Record) {
        val records = all().filterNot { it.id == record.id } + record
        val arr = JSONArray()
        records.forEach { r -> arr.put(JSONObject().put("id", r.id).put("name", r.name)
            .put("mnemonic", r.mnemonic).put("passphrase", r.passphrase).put("address", r.address)
            .put("path", r.path).put("type", r.type)) }
        val plain = arr.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val output = cipher.iv + cipher.doFinal(plain)
        val tmp = File(context.filesDir, "wallets.v1.tmp")
        tmp.writeBytes(output)
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
        plain.fill(0); output.fill(0)
    }
    @Synchronized fun delete(id: String) {
        val remaining = all().filterNot { it.id == id }
        val arr = JSONArray()
        remaining.forEach { r -> arr.put(JSONObject().put("id", r.id).put("name", r.name)
            .put("mnemonic", r.mnemonic).put("passphrase", r.passphrase).put("address", r.address)
            .put("path", r.path).put("type", r.type)) }
        val plain = arr.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val out = cipher.iv + cipher.doFinal(plain)
        file.writeBytes(out); plain.fill(0); out.fill(0)
    }
    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}