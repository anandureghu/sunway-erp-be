# Sunway backend cmd

Interactive Node.js admin CLI for destructive / maintenance ops against the same MySQL + Azure storage the Spring app uses.

## Setup

```bash
cd backend/cmd
npm install
```

Credentials load from:

1. [`../.env`](../.env) (backend root — same as the Java app)
2. Optional [`./.env`](./.env) overrides (see [`.env.example`](./.env.example))

Required vars: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS`, `AZURE_STORAGE_CONNECTION_STRING`.

Containers default to `erp-public` / `erp-private` (override with `AZURE_PUBLIC_CONTAINER` / `AZURE_PRIVATE_CONTAINER`).

## Run

```bash
npm start
# or
node src/index.js
```

Pick an operation from the menu. Destructive actions require typing `DELETE`.

Against non-local `DB_HOST`, set `ALLOW_DESTRUCTIVE_OPS=1` or the tool refuses to run.

## Operations

### Clear all finance invoices

Deletes:

- All rows in `invoices` (sales + purchase)
- Related `credit_notes` (FK)
- `payments` whose `invoice_id` matches those invoice codes
- Azure blobs for invoice `pdf_url` / `receipt_pdf_url` / `vendor_invoice_document_url` and payment `pdf_url`
- Matching `stored_files` ledger rows

Also nulls `transactions.invoice_id` for those codes (GL rows kept).

Does **not**:

- Touch `subscription_invoices`
- Reset sales / purchase order paid or outstanding amounts
- Reverse bank balances or journal amounts

## Adding an operation

1. Create `src/ops/your-op.js` exporting `{ id, label, run(ctx) }`
2. Import and append it to `OPERATIONS` in [`src/index.js`](src/index.js)
