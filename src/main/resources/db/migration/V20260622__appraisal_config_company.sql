-- Make appraisal configuration company-specific.
--
-- company_id is NULLABLE on purpose: existing rows stay NULL and continue to act
-- as a shared "legacy/global" fallback config. New configs created through the
-- app are stamped with the creating company's id, and reads prefer a company's
-- own config, falling back to the global (NULL) one when none exists. This makes
-- the change backward-compatible with no data backfill required.

ALTER TABLE `appraisal_config`
  ADD COLUMN `company_id` BIGINT NULL AFTER `id`;

ALTER TABLE `appraisal_config`
  ADD CONSTRAINT `fk_appraisal_config_company`
  FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`);

CREATE INDEX `idx_appraisal_config_company_year`
  ON `appraisal_config` (`company_id`, `year`);
