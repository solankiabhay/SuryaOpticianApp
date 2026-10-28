# Surya Optician App

Android optical shop billing foundation built with Kotlin, Jetpack Compose, Room/SQLite and transactional repository logic.

## Build
Open in Android Studio with JDK 17 and Android SDK 35; sync Gradle and run the `app` configuration.

See [feature audit](docs/FEATURE_AUDIT.md) and [device acceptance checklist](docs/REAL_DEVICE_ACCEPTANCE.md).

WhatsApp sharing is manual and user-initiated. The app cannot verify delivery/read status. PDFs are stored in app-private storage and shared through FileProvider.
