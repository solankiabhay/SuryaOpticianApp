# Feature audit

The repository originally contained only README.md. This branch adds a runnable Android project scaffold and core billing infrastructure. Status is source-level only; build and device behavior still need verification.

| Area | Status | Notes |
|---|---|---|
| Kotlin + Compose + Room | Implemented baseline | Android project, Room v1 entities and DAO |
| Transactional billing | Implemented baseline | Invoice, items, initial payment and stock updates use one Room transaction |
| Invoice numbering | Implemented baseline | Unique DB index plus timestamp/collision check |
| PDF generation and preview | Partial | Local PDF; single-page layout currently truncates very long bills |
| Secure PDF sharing | Partial | FileProvider, content URI and read grant |
| WhatsApp PDF sharing | Partial | User-initiated document share; exact recipient routing varies by WhatsApp version and requires device test |
| Phone normalization | Implemented baseline | 10-digit Indian number, +91 and leading zero supported |
| Invoice search | Partial | Invoice number, customer and phone; date filter not included |
| Payments/history | Partial | Persistence and transactional payment method; payment-entry UI not included |
| Reminder | Partial | Prefilled external WhatsApp link; add explicit confirmation dialog before production use |
| Customers/products | Partial | Data entities and read-only list/dashboard; create/edit forms not yet implemented |
| Prescription management | Data model only | Right/left fields exist; UI and DAO flows not completed |
| Reports/settings editor/logo | Partial | Basic outstanding dashboard and settings model; reports/settings screens absent |
| Print, backup/export, authentication | Not implemented | Must be added |
| Migrations | Not implemented beyond schema v1 | Export schema and write/test explicit migrations before schema changes |
| Automated build/device acceptance | Not run | Requires Android SDK/Gradle and a physical Android device for WhatsApp verification |

Do not describe WhatsApp opening as delivery/read confirmation.
