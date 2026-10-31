# Surya APK install and update testing

The Android package id stays `in.surya.optician`. The Android installer decides whether to show **Install** (not installed) or **Update** (same package and signing certificate already installed). The app UI is unchanged.

## One-time encrypted signing setup

This repository is public, so the raw keystore must never be committed. The one-time **Initialize Surya signing key** workflow generates a JKS keystore on a temporary GitHub runner, encrypts it with AES-256-CBC/PBKDF2, and commits only the encrypted file at `.github/signing/surya-upload-key.jks.enc`. The plaintext keystore is not uploaded as an artifact or printed in logs.

1. Open **Repository → Settings → Secrets and variables → Actions**.
2. Add one repository secret named `SURYA_BOOTSTRAP_PASSWORD` with the strong password provided to the repository owner through the setup instructions.
3. Open **Actions → Initialize Surya signing key → Run workflow** and select the branch that contains this workflow.
4. Wait for the workflow to succeed. It refuses to overwrite an existing encrypted key.
5. Open **Actions → Android build and tests → Run workflow** on the same branch to build the first APK.

The Android build decrypts the committed encrypted keystore only inside the temporary runner and uses the same password for the keystore and key. Do not change or delete `SURYA_BOOTSTRAP_PASSWORD`, and do not replace the encrypted keystore after distributing an APK. Anyone who gets both the encrypted file and this secret can use the signing identity, so keep the secret private.

## Test flow

1. Download the workflow artifact named `surya-apk`; the APK file inside is named `surya.apk`.
2. On a phone where Surya Optician is not installed, open `surya.apk` and Android shows **Install**.
3. Install and test the app. Keep it installed.
4. Run the Android workflow again for a later build and download the new `surya.apk`.
5. Open the new APK on the same phone. Because package id and signing key match and the workflow increments `versionCode`, Android shows **Update**. Tap Update; do not uninstall.
6. Verify existing customer, bill and prescription data is still present.

Local Android Studio debug builds may use the developer's local debug key and are not interchangeable with the CI-signed APK.
