-- Per-company total storage quota (cloud + database) on subscriptions, with plan-level defaults (5 GiB).

ALTER TABLE `subscription_plans`
  ADD COLUMN `default_max_storage_bytes` BIGINT NOT NULL DEFAULT 5368709120
  AFTER `default_amount`;

UPDATE `subscription_plans`
SET `default_max_storage_bytes` = 5368709120;

ALTER TABLE `company_subscriptions`
  ADD COLUMN `max_storage_bytes` BIGINT NOT NULL DEFAULT 5368709120
  AFTER `inventory_entitled`;

UPDATE `company_subscriptions` cs
INNER JOIN `subscription_plans` sp ON sp.`code` = cs.`plan_type`
SET cs.`max_storage_bytes` = sp.`default_max_storage_bytes`;
