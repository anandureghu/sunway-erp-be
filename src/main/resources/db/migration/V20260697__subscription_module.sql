-- SaaS subscription module: plan catalog, per-company subscriptions, manual payments, reminder logs.

CREATE TABLE `subscription_plans` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(20) NOT NULL,
  `name` VARCHAR(80) NOT NULL,
  `default_billing_days` INT NULL,
  `default_amount` DECIMAL(19, 4) NULL,
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subscription_plans_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `subscription_plans` (`code`, `name`, `default_billing_days`, `default_amount`, `active`) VALUES
  ('FREE', 'Free', NULL, 0.0000, 1),
  ('MONTHLY', 'Monthly', 30, NULL, 1),
  ('YEARLY', 'Yearly', 365, NULL, 1),
  ('CUSTOM', 'Custom', NULL, NULL, 1);

CREATE TABLE `company_subscriptions` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT NOT NULL,
  `plan_type` VARCHAR(20) NOT NULL,
  `amount` DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
  `currency_code` VARCHAR(10) NULL,
  `starts_at` DATE NOT NULL,
  `ends_at` DATE NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `warning_days` INT NOT NULL DEFAULT 7,
  `grace_days` INT NOT NULL DEFAULT 0,
  `hr_entitled` TINYINT(1) NOT NULL DEFAULT 1,
  `finance_entitled` TINYINT(1) NOT NULL DEFAULT 1,
  `inventory_entitled` TINYINT(1) NOT NULL DEFAULT 1,
  `notes` VARCHAR(1000) NULL,
  `created_by` VARCHAR(50) NULL,
  `updated_by` VARCHAR(50) NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_subscriptions_company` (`company_id`),
  KEY `idx_company_subscriptions_status` (`status`),
  KEY `idx_company_subscriptions_ends_at` (`ends_at`),
  CONSTRAINT `fk_company_subscriptions_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `subscription_payments` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_subscription_id` BIGINT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `amount` DECIMAL(19, 4) NOT NULL,
  `paid_on` DATE NOT NULL,
  `method_note` VARCHAR(255) NULL,
  `period_start` DATE NULL,
  `period_end` DATE NULL,
  `recorded_by` BIGINT NULL,
  `idempotency_key` VARCHAR(80) NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subscription_payments_idempotency` (`idempotency_key`),
  KEY `idx_subscription_payments_company` (`company_id`),
  KEY `idx_subscription_payments_subscription` (`company_subscription_id`),
  KEY `idx_subscription_payments_paid_on` (`paid_on`),
  CONSTRAINT `fk_subscription_payments_subscription` FOREIGN KEY (`company_subscription_id`) REFERENCES `company_subscriptions` (`id`),
  CONSTRAINT `fk_subscription_payments_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `subscription_reminder_logs` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_subscription_id` BIGINT NOT NULL,
  `reminder_type` VARCHAR(20) NOT NULL,
  `period_key` VARCHAR(40) NOT NULL,
  `sent_at` DATETIME(6) NOT NULL,
  `to_email` VARCHAR(120) NULL,
  `success` TINYINT(1) NOT NULL DEFAULT 0,
  `error` VARCHAR(1000) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subscription_reminder_once` (`company_subscription_id`, `reminder_type`, `period_key`),
  KEY `idx_subscription_reminder_subscription` (`company_subscription_id`),
  CONSTRAINT `fk_subscription_reminder_subscription` FOREIGN KEY (`company_subscription_id`) REFERENCES `company_subscriptions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Backfill FREE open-ended subscriptions for existing tenants.
INSERT INTO `company_subscriptions` (
  `company_id`, `plan_type`, `amount`, `currency_code`, `starts_at`, `ends_at`, `status`,
  `warning_days`, `grace_days`, `hr_entitled`, `finance_entitled`, `inventory_entitled`,
  `created_at`, `updated_at`
)
SELECT
  c.`id`,
  'FREE',
  0.0000,
  cur.`currency_code`,
  CURDATE(),
  NULL,
  'ACTIVE',
  7,
  0,
  COALESCE(c.`is_hr_enabled`, 1),
  COALESCE(c.`is_finance_enabled`, 1),
  COALESCE(c.`is_inventory_enabled`, 1),
  UTC_TIMESTAMP(6),
  UTC_TIMESTAMP(6)
FROM `companies` c
LEFT JOIN `currency` cur ON cur.`id` = c.`currency`
WHERE NOT EXISTS (
  SELECT 1 FROM `company_subscriptions` cs WHERE cs.`company_id` = c.`id`
);
