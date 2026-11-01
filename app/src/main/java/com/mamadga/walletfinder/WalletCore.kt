package com.mamadga.walletfinder

import org.bitcoinj.base.BitcoinNetwork
import org.bitcoinj.base.ScriptType
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.wallet.DeterministicSeed
import java.security.SecureRandom

/** Bitcoin-only key derivation. This alpha supports BIP44 and BIP84 only. */
object WalletCore {
    enum class AddressType(val purpose: Int, val script: ScriptType, val label: String) {
        LEGACY(44, ScriptType.P2PKH, "Legacy / BIP44"),
        NATIVE_SEGWIT(84, ScriptType.P2WPKH, "Native SegWit / BIP84")
    }
    data class CreatedWallet(val mnemonic: String, val address: String, val path: String, val addressType: AddressType)

    fun create(wordCount: Int = 12, passphrase: String = "", type: AddressType = AddressType.NATIVE_SEGWIT): CreatedWallet {
        require(wordCount == 12 || wordCount == 24) { "Only 12 or 24 BIP39 words are supported" }
        val bits = if (wordCount == 12) 128 else 256
        val seed = DeterministicSeed.ofRandom(SecureRandom(), bits, passphrase)
        val phrase = seed.mnemonicString ?: error("BIP39 mnemonic generation failed")
        return derive(phrase, passphrase, type)
    }
    fun derive(mnemonic: String, passphrase: String = "", type: AddressType = AddressType.NATIVE_SEGWIT): CreatedWallet {
        val seed = DeterministicSeed.ofMnemonic(mnemonic.trim().lowercase().replace(Regex("\\s+"), " "), passphrase)
        seed.check()
        val seedBytes = seed.seedBytes ?: error("BIP39 seed unavailable")
        var key: DeterministicKey = HDKeyDerivation.createMasterPrivateKey(seedBytes)
        listOf(ChildNumber(type.purpose, true), ChildNumber(0, true), ChildNumber(0, true), ChildNumber(0, false), ChildNumber(0, false))
            .forEach { key = HDKeyDerivation.deriveChildKey(key, it) }
        val address = key.toAddress(type.script, BitcoinNetwork.MAINNET).toString()
        seedBytes.fill(0)
        return CreatedWallet(seed.mnemonicString ?: mnemonic, address, "m/${type.purpose}'/0'/0'/0/0", type)
    }
    fun validateMainnetAddress(address: String): Boolean = try {
        val parsed = org.bitcoinj.base.AddressParser.getDefault(BitcoinNetwork.MAINNET).parseAddress(address.trim())
        parsed.network() == BitcoinNetwork.MAINNET
    } catch (_: Exception) { false }
    fun privateKeyWif(mnemonic: String, passphrase: String, type: AddressType): String {
        val seed = DeterministicSeed.ofMnemonic(mnemonic, passphrase)
        seed.check()
        val seedBytes = seed.seedBytes ?: error("BIP39 seed unavailable")
        var key = HDKeyDerivation.createMasterPrivateKey(seedBytes)
        listOf(ChildNumber(type.purpose, true), ChildNumber(0, true), ChildNumber(0, true), ChildNumber(0, false), ChildNumber(0, false))
            .forEach { key = HDKeyDerivation.deriveChildKey(key, it) }
        val wif = key.getPrivateKeyEncoded(BitcoinNetwork.MAINNET).toString()
        seedBytes.fill(0)
        return wif
    }
}