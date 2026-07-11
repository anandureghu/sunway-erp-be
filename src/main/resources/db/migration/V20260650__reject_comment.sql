-- Capture the approver's reason when a leave or loan request is rejected, so the
-- employee (and history views) can see why it was declined.
ALTER TABLE `employee_leaves` ADD COLUMN `rejection_comment` VARCHAR(1000) NULL;
ALTER TABLE `employee_loans`  ADD COLUMN `rejection_comment` VARCHAR(1000) NULL;
