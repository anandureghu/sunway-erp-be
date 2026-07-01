ALTER TABLE `stock_variances`
    ADD COLUMN `archived` tinyint(1) NOT NULL DEFAULT 0 AFTER `rejected_at`;

CREATE INDEX `idx_stock_variances_company_status_archived`
    ON `stock_variances` (`company_id`, `variance_status`, `archived`);
