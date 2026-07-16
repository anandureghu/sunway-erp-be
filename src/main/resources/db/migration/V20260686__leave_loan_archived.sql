-- Archive flag for decided leave / loan records so HR can move old rows out of
-- the active "Leave Approvals" / "Loan Approvals" lists (they remain viewable in
-- an archived view). Defaults to not-archived for all existing rows.
ALTER TABLE `employee_leaves` ADD COLUMN `archived` TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE `employee_loans`  ADD COLUMN `archived` TINYINT(1) NOT NULL DEFAULT 0;
