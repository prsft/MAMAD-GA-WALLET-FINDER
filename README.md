# MAMAD GA WALLET FINDER

**Version:** 0.1.0-alpha  
**Platform:** Android (min API 26; target API 35)  
**Network:** Bitcoin Mainnet only  
**Status:** alpha source; build and device compatibility are not yet fully verified.

> **Important:** This is not a production-audited wallet. Do not deposit significant funds. Independently review the source and test recovery before any real-money use. Never share a recovery phrase or private key.

## Included in this alpha
- On-device BIP39 wallet generation with 12 or 24 words using SecureRandom through bitcoinj.
- BIP39 recovery phrase import and checksum validation.
- BIP32 derivation of the first external receiving key:
  - BIP44 `m/44'/0'/0'/0/0` → Legacy P2PKH.
  - BIP84 `m/84'/0'/0'/0/0` → Native SegWit P2WPKH.
- Bitcoin Mainnet address validation.
- Multiple local wallet records stored as AES-256-GCM ciphertext in app-private storage with an Android Keystore key.
- Public-address balance and recent transaction lookup via Blockstream Esplora HTTPS API.
- QR display, address copy/share, and local Native SegWit vanity-prefix search.
- Persian/English UI toggle.
- Screenshot/recents protection via FLAG_SECURE; recovery phrase and WIF reveal gated by device authentication.

## Not implemented
- BIP49 Nested SegWit and BIP86 Taproot derivation.
- Address index/gap-limit wallet scanning; only external index 0 is derived.
- Transaction creation, signing, fee selection, and broadcast.
- Encrypted backup export/import, QR scanning, independent app PIN, multisig, Lightning, coin control, hardware wallet support.
- Full transaction classification or pagination beyond the first Esplora response page.

## Privacy and security
- Mnemonic and private keys are never sent to the balance API. Only public addresses are queried.
- The Esplora provider can correlate the queried public address with the user's IP address.
- Wallet records are encrypted using AES/GCM/NoPadding; Android cloud backup is disabled.
- This alpha has not undergone an independent security audit. Android clipboard, compromised/rooted devices, malware, accessibility services, and OS vulnerabilities can expose secrets. JVM strings cannot be reliably zeroized.
- The biometric/device-credential prompt is an access gate for revealing secrets; the Keystore encryption key is not configured to require per-operation biometric authentication.

## Build
Open the project in Android Studio with JDK 17 and Android SDK Platform 35. Run unit tests and `assembleDebug`. The cloud CI workflow also builds the debug APK and uploads it as a GitHub Actions artifact when compilation and tests pass.

## Device compatibility
The minimum SDK is 26, so Android 11 is within the declared API range. Samsung Galaxy A30 physical-device installation and runtime behavior still require real-device verification.
