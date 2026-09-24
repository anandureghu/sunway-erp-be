import { input } from "@inquirer/prompts";
import { assertDestructiveAllowed } from "../lib/env.js";
import { withTransaction } from "../lib/db.js";
import { deleteByPublicUrl } from "../lib/azure.js";

function uniq(urls) {
  return [...new Set(urls.filter((u) => u && String(u).trim()))];
}

/**
 * Wipe all finance invoices (sales + purchase), related credit notes & payments,
 * Azure PDF blobs, and stored_files ledger rows. Leaves subscription_invoices alone.
 */
export async function runClearInvoices(ctx) {
  const { pool, dbConfig, blobService } = ctx;
  assertDestructiveAllowed(dbConfig.host);

  const [invoices] = await pool.query(
    `SELECT id, invoice_id, company_id, pdf_url, receipt_pdf_url, vendor_invoice_document_url
     FROM invoices`
  );

  if (invoices.length === 0) {
    console.log("No invoices found. Nothing to do.");
    return;
  }

  const invoiceIds = invoices.map((r) => r.id);
  const invoiceCodes = invoices
    .map((r) => r.invoice_id)
    .filter((c) => c != null && String(c).trim() !== "");

  let payments = [];
  if (invoiceCodes.length > 0) {
    const [rows] = await pool.query(
      `SELECT id, invoice_id, pdf_url FROM payments WHERE invoice_id IN (?)`,
      [invoiceCodes]
    );
    payments = rows;
  }

  const [[{ creditNoteCount }]] = await pool.query(
    `SELECT COUNT(*) AS creditNoteCount FROM credit_notes WHERE invoice_id IN (?)`,
    [invoiceIds]
  );

  console.log("\n── Clear all finance invoices ──");
  console.log(`  DB: ${dbConfig.host}:${dbConfig.port}/${dbConfig.database}`);
  console.log(`  Invoices:     ${invoices.length}`);
  console.log(`  Credit notes: ${creditNoteCount}`);
  console.log(`  Payments:     ${payments.length}`);
  console.log(
    "  Also: delete invoice/payment PDF blobs + stored_files; null transactions.invoice_id"
  );
  console.log(
    "  Does NOT: reset sales/PO paid state, reverse GL balances, touch subscription_invoices\n"
  );

  const confirm = await input({
    message: 'Type DELETE to confirm (or anything else to cancel):',
  });
  if (confirm.trim() !== "DELETE") {
    console.log("Cancelled.");
    return;
  }

  const urls = uniq([
    ...invoices.flatMap((i) => [
      i.pdf_url,
      i.receipt_pdf_url,
      i.vendor_invoice_document_url,
    ]),
    ...payments.map((p) => p.pdf_url),
  ]);

  let blobsOk = 0;
  let blobsFail = 0;
  let blobsMissing = 0;

  const deleteLedger = async (blobPath) => {
    await pool.query(`DELETE FROM stored_files WHERE blob_path = ?`, [blobPath]);
  };

  console.log(`\nDeleting ${urls.length} blob URL(s)…`);
  for (const url of urls) {
    const result = await deleteByPublicUrl(blobService, url, deleteLedger);
    if (!result.blobPath) {
      blobsMissing += 1;
      console.warn(`  skip (unparseable URL): ${url}`);
      continue;
    }
    if (result.deleted) {
      blobsOk += 1;
    } else if (result.error) {
      blobsFail += 1;
      console.warn(`  fail ${result.blobPath}: ${result.error}`);
    } else {
      // Path parsed; blob already gone — still cleared ledger via callback
      blobsMissing += 1;
    }
  }

  const dbResult = await withTransaction(pool, async (conn) => {
    let deletedCreditNotes = 0;
    let deletedPayments = 0;
    let nulledTransactions = 0;
    let deletedInvoices = 0;

    if (invoiceIds.length > 0) {
      const [cn] = await conn.query(
        `DELETE FROM credit_notes WHERE invoice_id IN (?)`,
        [invoiceIds]
      );
      deletedCreditNotes = cn.affectedRows ?? 0;
    }

    if (invoiceCodes.length > 0) {
      const [pay] = await conn.query(
        `DELETE FROM payments WHERE invoice_id IN (?)`,
        [invoiceCodes]
      );
      deletedPayments = pay.affectedRows ?? 0;

      const [tx] = await conn.query(
        `UPDATE transactions SET invoice_id = NULL WHERE invoice_id IN (?)`,
        [invoiceCodes]
      );
      nulledTransactions = tx.affectedRows ?? 0;
    }

    const [inv] = await conn.query(`DELETE FROM invoices`);
    deletedInvoices = inv.affectedRows ?? 0;

    return {
      deletedCreditNotes,
      deletedPayments,
      nulledTransactions,
      deletedInvoices,
    };
  });

  console.log("\n── Done ──");
  console.log(`  Invoices deleted:      ${dbResult.deletedInvoices}`);
  console.log(`  Credit notes deleted:  ${dbResult.deletedCreditNotes}`);
  console.log(`  Payments deleted:      ${dbResult.deletedPayments}`);
  console.log(`  Transactions unlinked: ${dbResult.nulledTransactions}`);
  console.log(
    `  Blobs: deleted=${blobsOk} missing/unparseable=${blobsMissing} failed=${blobsFail}`
  );
}

export default {
  id: "clear-invoices",
  label: "Clear all finance invoices (DB + Azure PDFs)",
  run: runClearInvoices,
};
