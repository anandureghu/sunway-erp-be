-- Platform subscription invoices (generate / email / download history).

CREATE TABLE `subscription_invoices` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_subscription_id` BIGINT NOT NULL,
  `company_id` BIGINT NOT NULL,
  `invoice_no` VARCHAR(40) NOT NULL,
  `period_key` VARCHAR(40) NOT NULL,
  `period_start` DATE NOT NULL,
  `period_end` DATE NULL,
  `amount` DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
  `currency_code` VARCHAR(10) NULL,
  `plan_type` VARCHAR(20) NOT NULL,
  `pdf_path` VARCHAR(500) NULL,
  `pdf_url` VARCHAR(1000) NULL,
  `to_email` VARCHAR(120) NULL,
  `sent_at` DATETIME(6) NULL,
  `sent_by` VARCHAR(50) NULL,
  `send_success` TINYINT(1) NOT NULL DEFAULT 0,
  `send_error` VARCHAR(1000) NULL,
  `created_at` DATETIME(6) NOT NULL,
  `created_by` VARCHAR(50) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subscription_invoices_no` (`invoice_no`),
  UNIQUE KEY `uk_subscription_invoices_period` (`company_subscription_id`, `period_key`),
  KEY `idx_subscription_invoices_company` (`company_id`),
  KEY `idx_subscription_invoices_subscription` (`company_subscription_id`),
  CONSTRAINT `fk_subscription_invoices_subscription`
    FOREIGN KEY (`company_subscription_id`) REFERENCES `company_subscriptions` (`id`),
  CONSTRAINT `fk_subscription_invoices_company`
    FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
