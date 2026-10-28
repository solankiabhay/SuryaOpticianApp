# Surya Optician App

Android optical-shop billing MVP built with Kotlin, Jetpack Compose and Room/SQLite.

## Current features
- Customer creation, edit, search and guarded deletion (customers with history are retained)
- Product creation, edit, search by name/SKU/category, purchase cost, selling price, stock adjustment, category summaries and guarded deletion
- Multi-item invoice cart with line discounts/tax, optional existing-customer ID linking and initial payment
- Transactional stock deduction, invoice persistence and additional payment collection
- Expanded eye prescription entry (OD/OS SPH, CYL, axis, ADD, PD and notes), customer selection, edit/delete, history and prescription PDF sharing
- Shop invoice settings and invoice search
- Reports with invoice count, sales, collected, outstanding, estimated gross profit, stock valuation and category/low-stock summaries; optional inclusive date range (YYYY-MM-DD), customer/invoice/phone search and date-filtered CSV export
- Payment history by invoice and historical item-cost snapshots for new invoices
- Explicit Room migrations from database v1 to v3
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
This remains an MVP, not a production-ready billing product. Authentication/role management and dedicated in-app print integration remain incomplete. Database backup/restore is implemented in Settings but must be verified before production use. Category and cost reports are estimates; v1 invoices have no historical cost snapshot, so their profit is not reliable. Direct WhatsApp recipient routing depends on the installed WhatsApp version and must be confirmed by the user before sending. Date filters affect invoice summaries and CSV exports; dates are entered as YYYY-MM-DD in the device local time zone. The app cannot verify WhatsApp recipient selection, message delivery or read status. Build/test results and physical-device acceptance must pass before real shop use.
