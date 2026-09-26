-- Extend platform_settings with bank fields needed for subscription invoice Payment Information.
ALTER TABLE platform_settings
  ADD COLUMN account_holder VARCHAR(150) NULL AFTER bank_name,
  ADD COLUMN ifsc_code VARCHAR(32) NULL AFTER iban,
  ADD COLUMN branch_name VARCHAR(100) NULL AFTER ifsc_code;
