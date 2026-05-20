ALTER TABLE companies
    MODIFY COLUMN invoice_qr_enabled bit(1) NOT NULL DEFAULT 0;
