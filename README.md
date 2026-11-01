# MAMAD GA WALLET FINDER

**Version:** 0.2.0-alpha  
**Platform:** Android (min API 26; target API 35)  
**Network:** Bitcoin Mainnet only  
**Status:** Expanded alpha source. CI compilation and published-vector tests are required before each build; physical-device verification and independent security audit remain outstanding.

> **Important:** This is not a production-audited wallet. Do not deposit significant funds. Verify recovery independently before use. Never share a recovery phrase or private key.

## Included
- On-device BIP39 generation with 12 or 24 words using SecureRandom and bitcoinj.
- BIP39 recovery phrase import and checksum validation.
- BIP32 derivation of the first external receiving key (account 0, change 0, index 0):
  - BIP44 `m/44'/0'/0'/0/0` → Legacy P2PKH.
  - BIP49 `m/49'/0'/0'/0/0` → Nested SegWit P2SH-P2WPKH.
  - BIP84 `m/84'/0'/0'/0/0` → Native SegWit P2WPKH.
  - BIP86 `m/86'/0'/0'/0/0` → Taproot single-key P2TR with BIP341 TapTweak.
- BIP86 tweaked private-key WIF export (not the untweaked internal key).
- Multiple local wallet records stored as AES-256-GCM ciphertext in app-private storage with an Android Keystore key.
- Public-address balance and recent transaction lookup via Blockstream Esplora HTTPS API.
- QR display, address copy/share, and local Native SegWit vanity-prefix search.
- Persian/English UI toggle.
- Screenshot/recents protection via FLAG_SECURE; recovery phrase and WIF reveal gated by device authentication.

## Not implemented / limitations
- Address discovery beyond the first receive address; no automatic account/gap-limit scan or change-address scan in the UI.
- Transaction creation, signing, fee selection, and broadcast.
- Encrypted backup export/import, QR scanning, independent app PIN, multisig, Lightning, coin control, hardware wallet support.
- Full transaction classification or pagination beyond the first Esplora response page.

## Privacy and security
- Mnemonic and private keys are never sent to the balance API. Only public addresses are queried.
- The Esplora provider can correlate the queried public address with the user's IP address.
- Wallet records are encrypted using AES/GCM/NoPadding; Android cloud backup is disabled.
- This alpha has not undergone an independent security audit. Android clipboard, compromised/rooted devices, malware, accessibility services, and OS vulnerabilities can expose secrets. JVM strings cannot be reliably zeroized.
- The biometric/device-credential prompt is an access gate for revealing secrets; the Keystore encryption key is not configured to require per-operation biometric authentication.
- Vanity search generates random local wallets and does not scan for wallets belonging to others or target funded addresses.

## Build and verification
Open the project in Android Studio with JDK 17 and Android SDK Platform 35. Run unit tests and `assembleDebug`. GitHub Actions builds the debug APK and uploads it as an artifact when compilation and tests pass.

The unit suite includes public BIP44, BIP49, BIP84 and BIP86 address vectors. Test mnemonic phrases are public examples and must never be used for real funds.

## Device compatibility
The minimum SDK is 26, so Android 11 is within the declared API range. Samsung Galaxy A30 physical-device installation, biometric behavior, storage behavior and runtime performance still require real-device verification.
