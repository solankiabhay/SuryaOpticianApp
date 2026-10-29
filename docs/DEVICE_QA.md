# Surya Optician — Device Acceptance Checklist

Run this checklist on a real Android phone after installing the CI-built debug APK. Use test data only.

## First launch and access
- [ ] Fresh install shows the branded owner setup screen; passwords are masked and confirmation is required.
- [ ] Create the first Owner account, close/reopen the app, and verify sign-in works.
- [ ] Configure an app PIN, lock/reopen the app, and verify correct and incorrect PIN handling.
- [ ] Create Manager and Staff accounts. Confirm Staff cannot edit/delete customer or product records, access Reports, manage users, or export/restore backups; Manager can manage records but cannot delete records or manage users; Owner has full control.

## Customers and inventory
- [ ] Add a customer with age blank, 0, and a valid age; reject negative, over-120, and malformed values.
- [ ] Search and edit a customer. Confirm a customer referenced by an invoice or prescription cannot be deleted.
- [ ] Add a product with selling price, cost, SKU, category, stock and low-stock threshold; search/edit it.
- [ ] Confirm products used on invoices cannot be deleted and low-stock alerts update correctly.

## Billing — critical workflow
- [ ] Create a bill by searching/selecting a customer and product; verify quantity, discount, tax, paid amount, and due amount calculations.
- [ ] Reject empty customer/product, invalid amounts, zero/negative quantity, and quantities beyond available stock.
- [ ] Preview, save, print/export/share the invoice PDF, and verify the invoice number and shop details.
- [ ] Open **Menu → Bill History**; search by invoice number and customer name/phone.
- [ ] Verify history shows 10 bills per page and Previous/Next work without duplicate or skipped rows.
- [ ] Open a bill, record a partial payment and then final payment; verify the due balance and transaction history.
- [ ] Verify WhatsApp invoice/reminder opens a prefilled message and does not send without the user's action.
- [ ] Owner-only delete: verify confirmation, stock restoration, and deletion of linked invoice items/payment history.

## Prescriptions, reports and backup
- [ ] Select a customer and enter right/left eye SPH, CYL, Axis, ADD, PD and notes; save, edit, export/share and print the prescription PDF.
- [ ] Verify reports for valid date ranges and reject malformed/impossible dates; compare totals against test invoices.
- [ ] Export a backup as Owner, restore it into a test install, and confirm customers, products, bills, payments, prescriptions and settings remain intact.
- [ ] Verify invalid, corrupted, unsupported-version and non-app database files are rejected without losing current data.

## Final usability pass
- [ ] Test on a narrow phone screen and with the keyboard open: bottom navigation labels remain legible and the Menu sheet opens, scrolls, and closes correctly.
- [ ] Confirm New Bill, Bills/Bill History, Customers and Menu remain reachable from every screen.
- [ ] Rotate the device (if supported), use Android Back, and verify forms/navigation do not unexpectedly lose data.
- [ ] Check TalkBack labels, contrast, tap targets, empty states, loading states and visible error messages.
