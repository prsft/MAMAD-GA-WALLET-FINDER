package com.mamadga.walletfinder

import org.bitcoinj.base.BitcoinNetwork
import org.bitcoinj.base.LegacyAddress
import org.bitcoinj.base.SegwitAddress
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.crypto.ECKey
import org.bitcoinj.crypto.HDKeyDerivation
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.wallet.DeterministicSeed
import org.bouncycastle.crypto.digests.RIPEMD160Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Bitcoin Mainnet BIP39/BIP32 single-key address derivation.
 * BIP49 and BIP86 output construction is implemented explicitly and covered by published vectors.
 */
object WalletCore {
    enum class AddressType(val purpose: Int, val label: String) {
        LEGACY(44, "Legacy / BIP44"),
        NESTED_SEGWIT(49, "Nested SegWit / BIP49"),
        NATIVE_SEGWIT(84, "Native SegWit / BIP84"),
        TAPROOT(86, "Taproot / BIP86")
    }

    data class CreatedWallet(
        val mnemonic: String,
        val address: String,
        val path: String,
        val addressType: AddressType
    )

    private val network = BitcoinNetwork.MAINNET
    private val curveParams = CustomNamedCurves.getByName("secp256k1")
    private val curve = curveParams.curve
    private val order = curveParams.n

    fun create(
        wordCount: Int = 12,
        passphrase: String = "",
        type: AddressType = AddressType.NATIVE_SEGWIT
    ): CreatedWallet {
        require(wordCount == 12 || wordCount == 24) { "Only 12 or 24 BIP39 words are supported" }
        val entropyBits = if (wordCount == 12) 128 else 256
        val seed = DeterministicSeed.ofRandom(SecureRandom(), entropyBits, passphrase)
        val phrase = seed.mnemonicString ?: error("BIP39 mnemonic generation failed")
        return derive(phrase, passphrase, type)
    }

    fun derive(
        mnemonic: String,
        passphrase: String = "",
        type: AddressType = AddressType.NATIVE_SEGWIT,
        account: Int = 0,
        change: Int = 0,
        addressIndex: Int = 0
    ): CreatedWallet {
        require(account >= 0 && change in 0..1 && addressIndex >= 0) { "Invalid derivation path index" }
        val normalized = mnemonic.trim().lowercase().replace(Regex("\\s+"), " ")
        val seed = DeterministicSeed.ofMnemonic(normalized, passphrase)
        seed.check()
        val seedBytes = seed.seedBytes ?: error("BIP39 seed unavailable")
        try {
            var key: DeterministicKey = HDKeyDerivation.createMasterPrivateKey(seedBytes)
            listOf(
                ChildNumber(type.purpose, true),
                ChildNumber(0, true),
                ChildNumber(account, true),
                ChildNumber(change, false),
                ChildNumber(addressIndex, false)
            ).forEach { key = HDKeyDerivation.deriveChildKey(key, it) }

            val address = when (type) {
                AddressType.LEGACY -> key.toAddress(org.bitcoinj.base.ScriptType.P2PKH, network).toString()
                AddressType.NESTED_SEGWIT -> nestedSegwitAddress(key)
                AddressType.NATIVE_SEGWIT -> key.toAddress(org.bitcoinj.base.ScriptType.P2WPKH, network).toString()
                AddressType.TAPROOT -> taprootAddress(key)
            }
            val path = "m/${type.purpose}'/0'/${account}'/$change/$addressIndex"
            return CreatedWallet(seed.mnemonicString ?: normalized, address, path, type)
        } finally {
            seedBytes.fill(0)
        }
    }

    private fun nestedSegwitAddress(key: DeterministicKey): String {
        // BIP49 redeemScript = 0 <HASH160(compressed_pubkey)>.
        val redeemScript = ScriptBuilder.createP2WPKHOutputScript(key).program()
        return LegacyAddress.fromScriptHash(network, hash160(redeemScript)).toString()
    }

    private data class TaprootKey(val outputKey: ByteArray, val tweakedPrivateKey: BigInteger)

    private fun taprootKey(key: DeterministicKey): TaprootKey {
        val internalPoint = curve.decodePoint(key.pubKey).normalize()
        // BIP340 x-only keys are lifted to the even-Y point.
        val evenPoint = if (internalPoint.affineYCoord.toBigInteger().testBit(0)) internalPoint.negate().normalize() else internalPoint
        val xOnly = to32(evenPoint.affineXCoord.toBigInteger())
        val tweak = BigInteger(1, taggedHash("TapTweak", xOnly))
        require(tweak < order) { "Invalid BIP341 TapTweak scalar" }
        val outputPoint = evenPoint.add(curveParams.g.multiply(tweak)).normalize()
        require(!outputPoint.isInfinity) { "Invalid BIP341 tweaked point" }
        val outputKey = to32(outputPoint.affineXCoord.toBigInteger())

        var privateScalar = key.privKey
        if (internalPoint.affineYCoord.toBigInteger().testBit(0)) {
            privateScalar = order.subtract(privateScalar)
        }
        val tweakedPrivate = privateScalar.add(tweak).mod(order)
        require(tweakedPrivate != BigInteger.ZERO) { "Invalid BIP341 tweaked private key" }
        return TaprootKey(outputKey, tweakedPrivate)
    }

    private fun taprootAddress(key: DeterministicKey): String {
        val taproot = taprootKey(key)
        return SegwitAddress.fromProgram(network, 1, taproot.outputKey).toString()
    }

    fun privateKeyWif(mnemonic: String, passphrase: String, type: AddressType): String {
        val seed = DeterministicSeed.ofMnemonic(mnemonic.trim().lowercase().replace(Regex("\\s+"), " "), passphrase)
        seed.check()
        val seedBytes = seed.seedBytes ?: error("BIP39 seed unavailable")
        try {
            var key: DeterministicKey = HDKeyDerivation.createMasterPrivateKey(seedBytes)
            listOf(
                ChildNumber(type.purpose, true), ChildNumber(0, true), ChildNumber(0, true),
                ChildNumber(0, false), ChildNumber(0, false)
            ).forEach { key = HDKeyDerivation.deriveChildKey(key, it) }
            val exportKey = if (type == AddressType.TAPROOT) {
                ECKey.fromPrivate(taprootKey(key).tweakedPrivateKey, true)
            } else key
            return exportKey.getPrivateKeyEncoded(network).toString()
        } finally {
            seedBytes.fill(0)
        }
    }

    fun validateMainnetAddress(address: String): Boolean = try {
        val parsed = org.bitcoinj.base.AddressParser.getDefault(network).parseAddress(address.trim())
        parsed.network() == network
    } catch (_: Exception) {
        false
    }

    private fun hash160(data: ByteArray): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256").digest(data)
        val ripemd = RIPEMD160Digest()
        ripemd.update(sha, 0, sha.size)
        return ByteArray(20).also { ripemd.doFinal(it, 0) }
    }

    private fun taggedHash(tag: String, message: ByteArray): ByteArray {
        val tagHash = MessageDigest.getInstance("SHA-256").digest(tag.toByteArray(Charsets.UTF_8))
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(tagHash)
        digest.update(tagHash)
        digest.update(message)
        return digest.digest()
    }

    private fun to32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val unsigned = if (raw.size == 33 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
        require(unsigned.size <= 32) { "Integer does not fit in 32 bytes" }
        return ByteArray(32).also { unsigned.copyInto(it, 32 - unsigned.size) }
    }
}
