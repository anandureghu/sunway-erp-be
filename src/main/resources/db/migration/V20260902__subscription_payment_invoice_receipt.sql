-- Link subscription payments to invoices and store receipt delivery metadata.

ALTER TABLE `subscription_payments`
  ADD COLUMN `subscription_invoice_id` BIGINT NULL AFTER `company_id`,
  ADD COLUMN `receipt_no` VARCHAR(40) NULL AFTER `idempotency_key`,
  ADD COLUMN `receipt_generated_at` DATETIME(6) NULL AFTER `receipt_no`,
  ADD COLUMN `receipt_sent_at` DATETIME(6) NULL AFTER `receipt_generated_at`,
  ADD COLUMN `receipt_sent_by` VARCHAR(50) NULL AFTER `receipt_sent_at`,
  ADD COLUMN `receipt_send_success` TINYINT(1) NOT NULL DEFAULT 0 AFTER `receipt_sent_by`,
  ADD COLUMN `receipt_send_error` VARCHAR(1000) NULL AFTER `receipt_send_success`,
  ADD COLUMN `receipt_to_email` VARCHAR(500) NULL AFTER `receipt_send_error`,
  ADD KEY `idx_subscription_payments_invoice` (`subscription_invoice_id`),
  ADD UNIQUE KEY `uk_subscription_payments_receipt_no` (`receipt_no`),
  ADD CONSTRAINT `fk_subscription_payments_invoice`
    FOREIGN KEY (`subscription_invoice_id`) REFERENCES `subscription_invoices` (`id`);
