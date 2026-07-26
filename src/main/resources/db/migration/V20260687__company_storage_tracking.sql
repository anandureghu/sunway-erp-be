-- Per-company storage tracking: a ledger of uploaded file sizes (cloud storage)
-- and a cached snapshot of estimated database storage per company.
CREATE TABLE `stored_files` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `company_id` BIGINT NOT NULL,
  `blob_path` VARCHAR(500) NOT NULL,
  `container` VARCHAR(20) NOT NULL,
  `size_bytes` BIGINT NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stored_files_blob_path` (`blob_path`),
  KEY `idx_stored_files_company_id` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- database_storage_bytes is a heuristic estimate (row count x avg row length across
-- company_id-scoped tables), refreshed by a nightly job and on manual recalculation.
CREATE TABLE `company_storage_stats` (
  `company_id` BIGINT NOT NULL,
  `database_storage_bytes` BIGINT NOT NULL DEFAULT 0,
  `calculated_at` DATETIME NULL,
  PRIMARY KEY (`company_id`),
  CONSTRAINT `fk_company_storage_stats_company` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
