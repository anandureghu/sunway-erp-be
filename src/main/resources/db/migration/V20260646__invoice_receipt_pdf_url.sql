-- Keep original invoice PDF and a separate receipt PDF after payment.

DROP PROCEDURE IF EXISTS add_invoice_receipt_pdf_url;
DELIMITER //
CREATE PROCEDURE add_invoice_receipt_pdf_url()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'invoices'
           AND COLUMN_NAME = 'receipt_pdf_url'
    ) THEN
        ALTER TABLE invoices
            ADD COLUMN receipt_pdf_url varchar(1024) DEFAULT NULL AFTER pdf_url;
    END IF;
END //
DELIMITER ;

CALL add_invoice_receipt_pdf_url();
DROP PROCEDURE add_invoice_receipt_pdf_url;
