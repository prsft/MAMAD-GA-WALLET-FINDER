package com.mamadga.walletfinder

import org.junit.Assert.*
import org.junit.Test

class WalletCoreTest {
    private val vector = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    @Test fun bip39RecoveryProducesNativeSegwitMainnetAddress() {
        val wallet = WalletCore.derive(vector, "TREZOR", WalletCore.AddressType.NATIVE_SEGWIT)
        assertTrue(wallet.address.startsWith("bc1q"))
        assertTrue(WalletCore.validateMainnetAddress(wallet.address))
        assertEquals("m/84'/0'/0'/0/0", wallet.path)
    }
    @Test fun legacyDerivationProducesMainnetAddress() {
        val wallet = WalletCore.derive(vector, "TREZOR", WalletCore.AddressType.LEGACY)
        assertTrue(wallet.address.startsWith("1"))
        assertTrue(WalletCore.validateMainnetAddress(wallet.address))
        assertEquals("m/44'/0'/0'/0/0", wallet.path)
    }
    @Test fun invalidChecksumIsRejected() {
        val invalid = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"
        assertThrows(Exception::class.java) { WalletCore.derive(invalid, "", WalletCore.AddressType.NATIVE_SEGWIT) }
    }
    @Test fun testnetAddressIsRejected() {
        assertFalse(WalletCore.validateMainnetAddress("tb1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"))
    }
}