# Feature audit

This audit describes the current implementation in the Android app branch. CI results and physical-device behavior must be checked separately; a source change is not proof that a feature has passed acceptance testing.

| Area | Status | Notes |
|---|---|---|
| Kotlin + Compose + Room | Implemented baseline | Android project, Room v1 entities and DAO |
| Transactional billing | Implemented baseline | Invoice, items, initial payment and linked-product stock updates use one Room transaction |
| Monetary calculations | Implemented baseline | Integer paise, discount validation and overflow-safe calculations; unit tests added |
| Invoice numbering | Implemented baseline | Unique DB index plus timestamp/collision check |
| Customers | Partial | Create customer form and customer-linked invoices; edit/delete and customer detail/history are not implemented |
| Products/inventory | Partial | Create product form, stock alerts and stock-linked checkout; edit/delete/stock-adjustment UI is not implemented |
| Billing | Partial | Quick invoice supports one stock-linked or free-text line; multi-line cart and invoice-level discount UI remain |
| Payments | Partial | Initial/partial payments are transactional and can be entered from an invoice; payment history screen and reversal/refund are not implemented |
| Prescription management | Partial | Right/left SPH/CYL/AXIS/ADD, PD and notes can be saved for a customer; browse/edit/print history is not implemented |
| Shop settings | Partial | Shop name, address, phone, GSTIN and footer editor; logo and tax configuration are not implemented |
| Sales/outstanding dashboard | Partial | Sales, collected, outstanding and low-stock indicators; date-range and category reports are not implemented |
| PDF generation and preview | Partial | Local PDF; single-page layout currently truncates long bills |
| Secure PDF sharing | Partial | FileProvider content URI and read grant |
| WhatsApp PDF sharing | Partial | User-initiated share sheet; Android cannot guarantee recipient preselection and physical-device verification remains required |
| Phone normalization | Implemented baseline | Pure Kotlin utility and tests for common Indian formats |
| Invoice search | Partial | Invoice number, customer and phone; date filter not included |
| Payment reminder | Partial | Confirmation dialog and prefilled WhatsApp URL; user must review and send manually |
| Print, backup/export, authentication | Not implemented | Must be added before production use |
| Migrations | Not implemented beyond schema v1 | No schema change in this branch; add and test explicit migrations before future schema changes |
| Automated build/unit tests | CI configured | Check GitHub Actions on the latest commit; do not infer a pass while a run is queued/in progress |
| Real-device acceptance | Not run | PDF viewer, WhatsApp recipient flow and real sending need physical Android testing |

Do not describe opening WhatsApp as delivery/read confirmation. Do not treat this build as production-ready until CI and real-device acceptance pass.
