# Surya Optician App

Android optical-shop billing MVP built with Kotlin, Jetpack Compose and Room/SQLite.

## Current features
- Customer and product creation with stock tracking and low-stock visibility
- Multi-item invoice cart with line discounts/tax, optional existing-customer ID linking and initial payment
- Transactional stock deduction, invoice persistence and additional payment collection
- Basic prescription entry and shop invoice settings
- Invoice search, multi-page PDF generation, CSV report export, PDF preview and user-initiated WhatsApp sharing
- Payment-reminder confirmation before opening WhatsApp

## Build and test
Requirements: JDK 17, Android SDK platform 35, Gradle 8.9.

The GitHub Actions workflow at `.github/workflows/android.yml` runs `testDebugUnitTest`, builds the debug APK, and uploads it as an artifact. Check the Actions tab / PR checks for the authoritative build result.

For local build from the repository root:
```bash
gradle testDebugUnitTest
gradle assembleDebug
```

See [feature audit](docs/FEATURE_AUDIT.md) and [device acceptance checklist](docs/REAL_DEVICE_ACCEPTANCE.md).

## Important limitations
This is an MVP, not a production-ready billing product. Multi-item cart, customer-to-invoice linking, customer/product edit/delete, comprehensive prescription fields/history, reports/export, print integration, backup/restore, authentication and explicit database migrations remain incomplete. The app cannot verify WhatsApp recipient selection, message delivery or read status. Test on a physical Android device before real shop use.
