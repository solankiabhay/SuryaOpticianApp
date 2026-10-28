# Physical Android acceptance checklist

- [ ] Create a customer and save a valid Indian phone number.
- [ ] Add products and stock.
- [ ] Create a multi-item invoice with discount, tax and partial payment.
- [ ] Verify invoice, line items, payment and stock update are atomic.
- [ ] Induce a transaction failure and confirm no partial data is committed.
- [ ] Generate and preview a PDF with correct shop, customer, line items and totals.
- [ ] Use the native Print action and verify print preview, page count, totals and printer/PDF destination.
- [ ] Share the PDF into WhatsApp and verify the attachment and intended recipient before manually sending.
- [ ] Disable/uninstall WhatsApp and verify native share fallback.
- [ ] Reopen and reshare an old invoice without creating a sale or deducting stock again.
- [ ] Record partial payment and verify outstanding amount/history.
- [ ] Confirm reminder contents before manually sending.
- [ ] Export a database backup to device storage and confirm the file is created.
- [ ] Restore that backup on a disposable/test installation and verify customers, products, invoices, payments and prescriptions remain intact.
- [ ] Confirm a corrupt/non-database file is rejected without replacing the current database.
- [ ] Verify migration from a v1 database preserves existing records.
- [ ] Restart the app and verify persistence.
- [ ] Verify no delivery/read claim is made merely because WhatsApp opened.

Record device model, Android/WhatsApp versions, build variant, date, and evidence. Checklist remains unverified until actually executed.
