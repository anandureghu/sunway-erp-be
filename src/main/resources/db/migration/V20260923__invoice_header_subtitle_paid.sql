-- Paid invoice header subtitle (unpaid remains invoice_header_subtitle).
ALTER TABLE company_invoice_settings
  ADD COLUMN invoice_header_subtitle_paid VARCHAR(200) NULL;
