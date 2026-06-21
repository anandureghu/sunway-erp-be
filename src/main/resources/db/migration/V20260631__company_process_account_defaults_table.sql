-- Self-healing creation of `company_process_account_defaults`.
--
-- Why this exists separately from V20260628:
-- V20260628 was applied in some environments (e.g. prod) while it was still the
-- HR permission seed, before the version was reassigned to this table. Those
-- environments have V20260628 marked as applied in flyway_schema_history, so
-- Flyway will never re-run it — leaving the table missing and breaking Hibernate
-- schema validation on boot. This migration runs as a fresh, unapplied version
-- and is fully idempotent, so it is a no-op anywhere the table already exists.

CREATE TABLE IF NOT EXISTS `company_process_account_defaults` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `company_id` bigint NOT NULL,
    `process_code` varchar(64) NOT NULL,
    `debit_account_id` bigint DEFAULT NULL,
    `credit_account_id` bigint DEFAULT NULL,
    `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_company_process_account_defaults` (`company_id`, `process_code`),
    CONSTRAINT `fk_company_process_account_defaults_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seed STOCK_VARIANCE from existing purchase defaults for backward compatibility.
-- INSERT IGNORE keeps this safe to re-run: the unique key skips rows already seeded
-- by V20260628 on environments where that migration created the table.
INSERT IGNORE INTO `company_process_account_defaults` (`company_id`, `process_code`, `debit_account_id`, `credit_account_id`)
SELECT
    `id`,
    'STOCK_VARIANCE',
    `default_purchase_debit_account_id`,
    `default_purchase_credit_account_id`
FROM `companies`
WHERE `default_purchase_debit_account_id` IS NOT NULL
   OR `default_purchase_credit_account_id` IS NOT NULL;