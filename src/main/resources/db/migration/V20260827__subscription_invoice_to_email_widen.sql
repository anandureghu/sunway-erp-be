-- Allow multiple invoice recipients (company + admin emails) in to_email.
ALTER TABLE `subscription_invoices`
  MODIFY COLUMN `to_email` VARCHAR(500) NULL;
