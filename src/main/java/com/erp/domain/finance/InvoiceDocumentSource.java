package com.erp.domain.finance;

/**
 * How the invoice PDF/document is sourced. GENERATED = ERP-rendered PDF (sales or PO-based purchase).
 */
public enum InvoiceDocumentSource {
    GENERATED,
    SUPPLIER_UPLOAD,
    EXTERNAL_LINK
}
