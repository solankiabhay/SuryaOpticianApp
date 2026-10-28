# Surya Optician App

Android optical-shop billing MVP built with Kotlin, Jetpack Compose and Room/SQLite.

## Current features
- Customer creation/editing and product creation/editing with stock adjustment and low-stock visibility
- Multi-item invoice cart with line discounts/tax, optional existing-customer ID linking and initial payment
- Transactional stock deduction, invoice persistence and additional payment collection
- Expanded eye prescription entry (OD/OS SPH, CYL, axis, ADD, PD and notes), customer selection and prescription history
- Shop invoice settings and invoice search
- Reports with invoice count, sales, collected and outstanding totals, optional inclusive date range (YYYY-MM-DD), customer/invoice/phone search, and date-filtered CSV export
- Multi-page invoice PDF generation/preview and user-initiated WhatsApp sharing
- Payment-reminder confirmation before opening WhatsApp
- Horizontally scrollable navigation on narrow phone screens

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
This remains an MVP, not a production-ready billing product. Customer edit/delete/search, product edit and stock-adjustment flows, a guided customer/product picker in billing, prescription edit/delete and prescription PDF/print, item/category reports, cost tracking/profit, print integration, backup/restore, authentication and explicit database migrations remain incomplete. Date filters affect invoice summaries and CSV exports; dates are entered as YYYY-MM-DD in the device local time zone. The app cannot verify WhatsApp recipient selection, message delivery or read status. Build/test results and physical-device acceptance must pass before real shop use.
