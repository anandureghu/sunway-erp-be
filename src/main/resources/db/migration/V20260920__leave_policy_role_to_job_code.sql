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
-- Some databases already received this rename while this migration briefly had the
-- duplicate V20260918 version; skip the ALTER in those databases.
DROP PROCEDURE IF EXISTS migrate_leave_policy_role_to_job_code;
DELIMITER //
CREATE PROCEDURE migrate_leave_policy_role_to_job_code()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'company_leave_policies'
           AND COLUMN_NAME = 'role'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'company_leave_policies'
           AND COLUMN_NAME = 'job_code'
    ) THEN
        ALTER TABLE company_leave_policies
            CHANGE COLUMN `role` `job_code` VARCHAR(255) NOT NULL;
    END IF;
END //
DELIMITER ;

CALL migrate_leave_policy_role_to_job_code();
DROP PROCEDURE migrate_leave_policy_role_to_job_code;
