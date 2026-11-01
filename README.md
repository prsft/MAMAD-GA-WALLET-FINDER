# MAMAD GA WALLET FINDER

An Android Studio project for an **offline, on-device Bitcoin mainnet key generator**. It creates a random secp256k1 private scalar using `SecureRandom`, derives a compressed-public-key legacy P2PKH address, and displays the private key as WIF and hex.

## Build an APK
1. Open this folder in Android Studio (current stable version).
2. Allow Gradle sync and dependency download.
3. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Android Studio will show the APK location when the build completes.

## Security notes
- This app does not scan addresses, query balances, or search for wallets belonging to others.
- No network permission is declared; key generation happens locally on the device.
- This is a sample project, not an audited wallet. Do not use it to store significant funds. Prefer a well-reviewed hardware or software wallet.
- Never share the WIF/HEX private key. Anyone who obtains it can spend the associated bitcoin.
- Importing a key into Trust Wallet, if supported by the installed version, must be done manually inside the wallet. This app does not silently inject keys into another app.
