-- Track when subscription invoice PDF was last generated (verify-before-send workflow).

ALTER TABLE `subscription_invoices`
  ADD COLUMN `generated_at` DATETIME(6) NULL AFTER `pdf_url`,
  ADD COLUMN `generated_by` VARCHAR(50) NULL AFTER `generated_at`;
