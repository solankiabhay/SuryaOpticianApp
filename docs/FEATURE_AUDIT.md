# Feature audit

The app now includes a starter Android UI and local Room-backed workflows, including a multi-item bill cart and shareable invoice CSV export. Status is source-level until GitHub Actions succeeds and real-device acceptance is completed.

| Area | Status | Notes |
|---|---|---|
| Kotlin + Compose + Room | Implemented baseline | Android project, Room v1 entities and DAO; schema export configured |
| Transactional billing | Implemented baseline | Invoice, items, initial payment and stock updates use one Room transaction |
| Invoice numbering | Implemented baseline | Unique DB index plus timestamp/collision check |
| Customer management | Basic create/list | Create form and list; edit/delete/search by customer still needed |
| Product/stock management | Basic create/list | Product entry, stock count and low-stock indicator; edit/stock-adjustment flow still needed |
| Billing | Basic multi-line cart | Add items to cart, optionally link an existing customer by ID, set quantity/line discount/tax and initial payment; richer product picker and customer search still needed |
| Payment collection | Basic | Records additional payments transactionally; lookup uses invoice database ID |
| Prescription management | Basic | Customer ID, right/left sphere and PD; full Rx fields/history/editing needed |
| Shop settings | Basic | Name/address/phone/GSTIN/footer stored locally |
| PDF generation and preview | Improved baseline | Creates multi-page PDFs for long item lists; physical PDF layout still needs QA |
| Secure PDF sharing | Partial | FileProvider, content URI and read grant |
| WhatsApp PDF sharing | Partial | User-initiated document share; exact recipient routing varies by WhatsApp version and requires device test |
| Phone normalization | Implemented baseline | 10-digit Indian number, +91 and leading zero supported |
| Invoice search | Partial | Invoice number, customer and phone; date filter not included |
| Payment reminder | Basic | Confirmation dialog before opening a prefilled WhatsApp message; user must review/send |
| Reports/export | Basic CSV export | Exports visible/filtered invoices to a shareable CSV; richer date/category reports still needed |
| Print, authentication | Not implemented | Add after core flows are verified |
| Migrations | Not implemented beyond schema v1 | Write/test explicit migrations before changing schema |
| Automated build/device acceptance | Pending | GitHub Actions workflow added; verify its result and test WhatsApp on a physical device |

Do not describe WhatsApp opening as delivery/read confirmation. This MVP is not production-ready until the build/test workflow passes, database migrations and backup are planned, and real-device acceptance is complete.
