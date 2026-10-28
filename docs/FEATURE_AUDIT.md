# Feature audit

This audit reflects source code currently on the feature branch. It is not a claim that the app has compiled or passed device acceptance.

| Area | Status | Notes |
|---|---|---|
| Kotlin + Compose + Room | Implemented baseline | Room database schema v3 with explicit v1→v2 and v2→v3 migrations |
| Transactional billing | Implemented baseline | Invoice, items, initial payment and stock updates share one Room transaction; monetary arithmetic uses overflow checks |
| Invoice numbering | Implemented baseline | Unique DB index plus timestamp/collision check |
| Customer management | Implemented baseline | Create/list/edit/search; delete is blocked when invoice or prescription history exists |
| Product/stock management | Implemented baseline | Create/list/edit/search by name/SKU/category; selling price, purchase cost, category, stock and low-stock threshold; delete blocked when invoice lines reference product |
| Billing | Improved baseline | Multi-item cart, selectable customer/product shortcuts, line discounts/tax and initial payment; product stock is checked transactionally |
| Payment collection | Improved baseline | Additional payments are transactional, overpayment is rejected, payment history is shown by invoice |
| Prescription management | Improved baseline | OD/OS SPH/CYL/axis/ADD, PD, notes, customer history, edit/delete and PDF sharing |
| Shop settings | Basic | Shop name/address/phone/GSTIN/footer stored locally |
| Invoice PDF | Implemented baseline | Multi-page PDF generation and preview; physical layout and long descriptions still need device QA |
| Invoice/CSV sharing | Partial | FileProvider sharing; WhatsApp direct-recipient hint is attempted, but the user must confirm the recipient and attachment in the installed WhatsApp version |
| Payment reminder | Basic | Confirmation dialog before opening a prefilled WhatsApp message; user must review/send |
| Sales reports/export | Improved baseline | Invoice count, sales, collected and due totals; inclusive From/To dates, invoice/customer/phone filter and date-filtered CSV |
| Inventory and category reports | Implemented baseline | Current stock units, purchase-cost valuation, retail valuation, low-stock list and category roll-up |
| Gross-profit estimate | Implemented baseline | Uses item cost snapshot captured on new invoices, excludes tax; migrated v1 invoice lines have cost zero, so their historical profit is not reliable |
| Database migrations | Implemented baseline | v1→v2 adds cost snapshots; v2→v3 adds product category |
| Database backup/restore | Implemented baseline | Settings exports a checkpointed SQLite database; restore validates integrity, version and required tables before replacing local DB. Must be tested on-device and use backups from this app |
| Local PIN lock | Implemented baseline | Optional 4–8 digit PIN, salted PBKDF2 hash, lock on next app open; no online recovery and no staff roles |
| Dedicated print integration | Not implemented | PDF can be opened/shared to a compatible viewer; direct in-app print flow remains |
| Automated build/device acceptance | Pending | Do not declare release-ready until Gradle tests/build and physical-device checklist pass |

Do not describe WhatsApp opening as delivery/read confirmation. This remains an MVP until build verification, backup/restore, access control decisions and real-device acceptance are complete.
