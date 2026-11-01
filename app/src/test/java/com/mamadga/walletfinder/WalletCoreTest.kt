package com.mamadga.walletfinder

import org.junit.Assert.*
import org.junit.Test

/** Public BIP39/BIP49/BIP84/BIP86 vectors only; never use these test words for funds. */
class WalletCoreTest {
    private val vector = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

    @Test fun bip44MatchesPublishedFirstReceiveAddress() {
        val wallet = WalletCore.derive(vector, "", WalletCore.AddressType.LEGACY)
        assertEquals("1LqBGSKuX5yYUonjxT5qGfpUsXKYYWeabA", wallet.address)
        assertEquals("m/44'/0'/0'/0/0", wallet.path)
        assertTrue(WalletCore.validateMainnetAddress(wallet.address))
    }

    @Test fun bip49MatchesPublishedNestedSegwitVector() {
        val wallet = WalletCore.derive(vector, "", WalletCore.AddressType.NESTED_SEGWIT)
        assertEquals("37VucYSaXLCAsxYyAPfbSi9eh4iEcbShgf", wallet.address)
        assertEquals("m/49'/0'/0'/0/0", wallet.path)
        assertTrue(WalletCore.validateMainnetAddress(wallet.address))
    }

    @Test fun bip84MatchesPublishedNativeSegwitVector() {
        val wallet = WalletCore.derive(vector, "", WalletCore.AddressType.NATIVE_SEGWIT)
        assertEquals("bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu", wallet.address)
        assertEquals("m/84'/0'/0'/0/0", wallet.path)
        assertTrue(WalletCore.validateMainnetAddress(wallet.address))
    }

    @Test fun bip86MatchesOfficialTaprootVector() {
        val wallet = WalletCore.derive(vector, "", WalletCore.AddressType.TAPROOT)
        assertEquals("bc1p5cyxnuxmeuwuvkwfem96lqzszd02n6xdcjrs20cac6yqjjwudpxqkedrcr", wallet.address)
        assertEquals("m/86'/0'/0'/0/0", wallet.path)
        assertTrue(WalletCore.validateMainnetAddress(wallet.address))
    }

    @Test fun derivationSupportsAccountAndAddressIndexes() {
        val wallet = WalletCore.derive(vector, "", WalletCore.AddressType.NATIVE_SEGWIT, account = 0, change = 1, addressIndex = 2)
        assertEquals("m/84'/0'/0'/1/2", wallet.path)
        assertTrue(wallet.address.startsWith("bc1q"))
    }

    @Test fun invalidChecksumIsRejected() {
        val invalid = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"
        assertThrows(Exception::class.java) { WalletCore.derive(invalid, "", WalletCore.AddressType.NATIVE_SEGWIT) }
    }

    @Test fun testnetAddressIsRejected() {
        assertFalse(WalletCore.validateMainnetAddress("tb1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh"))
    }

    @Test fun taprootWifExportsTweakedPrivateKey() {
        val wif = WalletCore.privateKeyWif(vector, "", WalletCore.AddressType.TAPROOT)
        assertTrue(wif.startsWith("K") || wif.startsWith("L"))
    }
}
