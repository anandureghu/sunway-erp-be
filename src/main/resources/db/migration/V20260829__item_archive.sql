-- Soft-delete (archive) support for inventory items.

ALTER TABLE `items`
  ADD COLUMN `archived` TINYINT(1) NOT NULL DEFAULT 0 AFTER `status`,
  ADD COLUMN `archived_at` DATETIME(6) NULL AFTER `archived`,
  ADD COLUMN `archived_by` BIGINT NULL AFTER `archived_at`;

CREATE INDEX `idx_items_company_archived` ON `items` (`company_id`, `archived`);
