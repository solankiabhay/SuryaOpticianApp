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
- Optional local PIN lock with salted PBKDF2 hash
- SQLite database backup export and validated restore via Android document picker
- Multi-page invoice PDF generation/preview, native Android print flow and user-initiated WhatsApp sharing
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

**App UI documentation:** [screen-by-screen layout, visual design, navigation map, and user-flow diagrams](docs/APP_LAYOUT_AND_FLOW.md). This documents the current implementation, including authentication, Dashboard, Customers (age included), Products, New Bill, Bills/PDF, Payments, Prescriptions, Reports, Settings, Users, local data and known limitations.\n\nAlso see the [feature audit](docs/FEATURE_AUDIT.md) and [device acceptance checklist](docs/REAL_DEVICE_ACCEPTANCE.md).

## Important limitations
This remains an MVP, not a production-ready billing product. Local role-based UI/action guards are implemented: Owner has full control, Manager can manage day-to-day records and reports but cannot delete records or manage users/backups/PIN, and Staff can perform routine billing/payment work but cannot edit inventory/customer records, edit existing bills, access reports, delete records, or manage users/backups/PIN. Owner-only backup export/restore is checked both in the UI and at the activity-result callback. Accounts and permissions are device-local; this is not server-side authorization and should not be treated as protection against a technically skilled person modifying the app or device. The optional local PIN lock and database backup/restore must be verified on a physical device before production use. Category and cost reports are estimates; v1 invoices have no historical cost snapshot, so their profit is not reliable. Direct WhatsApp recipient routing depends on the installed WhatsApp version and must be confirmed by the user before sending. Date filters affect invoice summaries and CSV exports; dates are entered as YYYY-MM-DD in the device local time zone. The app cannot verify WhatsApp recipient selection, message delivery or read status. Build/test results and physical-device acceptance must pass before real shop use.
