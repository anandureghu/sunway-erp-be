-- Soft-archive for subscription history tables that grow over time.
ALTER TABLE `subscription_payments`
  ADD COLUMN `archived` TINYINT(1) NOT NULL DEFAULT 0 AFTER `created_at`,
  ADD COLUMN `archived_at` DATETIME(6) NULL AFTER `archived`;

CREATE INDEX `idx_sub_pay_company_archived`
  ON `subscription_payments` (`company_id`, `archived`);

ALTER TABLE `subscription_invoices`
  ADD COLUMN `archived` TINYINT(1) NOT NULL DEFAULT 0 AFTER `created_by`,
  ADD COLUMN `archived_at` DATETIME(6) NULL AFTER `archived`;

CREATE INDEX `idx_sub_inv_company_archived`
  ON `subscription_invoices` (`company_id`, `archived`);

ALTER TABLE `subscription_reminder_logs`
  ADD COLUMN `archived` TINYINT(1) NOT NULL DEFAULT 0 AFTER `error`,
  ADD COLUMN `archived_at` DATETIME(6) NULL AFTER `archived`;

CREATE INDEX `idx_sub_rem_cs_archived`
  ON `subscription_reminder_logs` (`company_subscription_id`, `archived`);
