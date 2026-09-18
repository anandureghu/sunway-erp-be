-- Leave policies are configured per JOB CODE, not per company/security role
-- (a role governs system access only). Rename the key column role -> job_code.
--
-- This is intentionally NON-DESTRUCTIVE: existing rows keep their values (old role
-- names simply move into the job_code column) and continue to match employees at
-- runtime via LeavePolicyKeyResolver's legacy fallback (job code -> job title ->
-- company role -> security role). No policy is lost and no employee breaks on deploy.
--
-- MySQL retargets the existing UNIQUE index (company_id, role, leave_type) onto the
-- renamed column automatically, so it becomes (company_id, job_code, leave_type).
ALTER TABLE company_leave_policies
    CHANGE COLUMN `role` `job_code` VARCHAR(255) NOT NULL;
