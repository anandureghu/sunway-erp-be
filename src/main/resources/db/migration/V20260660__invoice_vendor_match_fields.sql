-- Vendor invoice matching: before a vendor payment is confirmed, the vendor's
-- own invoice must be reconciled against the purchase order (their invoice
-- number recorded and a supporting document attached). Kept separate from
-- pdf_url/document_source so matching never overwrites the system-generated
-- invoice PDF.

DROP PROCEDURE IF EXISTS add_invoice_vendor_match_fields;
DELIMITER //
CREATE PROCEDURE add_invoice_vendor_match_fields()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'invoices'
           AND COLUMN_NAME = 'vendor_invoice_document_url'
    ) THEN
        ALTER TABLE invoices
            ADD COLUMN vendor_invoice_document_url varchar(2000) DEFAULT NULL AFTER external_document_url,
            ADD COLUMN vendor_invoice_matched_at datetime(6) DEFAULT NULL AFTER vendor_invoice_document_url;
    END IF;
END //
DELIMITER ;

CALL add_invoice_vendor_match_fields();
DROP PROCEDURE add_invoice_vendor_match_fields;
