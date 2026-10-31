# Surya APK install and update testing

The Android package id stays `in.surya.optician`. The Android installer decides whether to show **Install** (not installed) or **Update** (same package and signing certificate already installed). The app UI is unchanged.

## One-time GitHub Actions signing setup

Do not commit the keystore or its passwords to this public repository. Create these repository Actions secrets:

- `SURYA_KEYSTORE_BASE64`: base64 text of the persistent `surya-upload-key.jks` file (Linux: `base64 -w0 surya-upload-key.jks`; macOS: `base64 < surya-upload-key.jks | tr -d '\n'`).
- `SURYA_KEYSTORE_PASSWORD`: keystore password.
- `SURYA_KEY_ALIAS`: key alias.
- `SURYA_KEY_PASSWORD`: key password.

Add them under **Repository → Settings → Secrets and variables → Actions → New repository secret**. Keep a secure backup of the keystore and passwords. Never regenerate or replace this key after distributing an APK; Android will reject updates signed with a different key.

## Test flow

1. Add all four secrets before running the Android workflow.
2. Download the workflow artifact named `surya-apk`; the APK file inside is named `surya.apk`.
3. On a phone where Surya Optician is not installed, open `surya.apk` and Android shows **Install**.
4. Install and test the app. Keep it installed.
5. Push a later build / run the workflow again and download the new `surya.apk`.
6. Open the new APK on the same phone. Because package id and signing key match and the workflow increments `versionCode`, Android shows **Update**. Tap Update; do not uninstall.
7. Verify existing customer, bill and prescription data is still present.

The workflow intentionally fails at the signing setup step if any secret is missing. Local Android Studio debug builds may still use the developer's local debug key and are not interchangeable with the CI-signed APK.
