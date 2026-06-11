-- Store the blob path of an uploaded passport / residence-permit scan.
-- Nullable: existing records simply have no attached document yet.

ALTER TABLE `employee_passports`
  ADD COLUMN `document_path` VARCHAR(512) NULL;

ALTER TABLE `employee_residence_permits`
  ADD COLUMN `document_path` VARCHAR(512) NULL;
