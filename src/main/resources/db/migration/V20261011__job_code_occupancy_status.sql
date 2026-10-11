-- Job-code status is now the position state (VACANT / OCCUPIED / ON_HOLD) instead of the
-- old approval workflow (PENDING_APPROVAL / APPROVED / REJECTED). ON_HOLD carries a comment.
ALTER TABLE job_codes
    ALTER COLUMN status SET DEFAULT 'VACANT';

ALTER TABLE job_codes
    ADD COLUMN hold_comment VARCHAR(500) NULL,
    ADD COLUMN hold_since DATE NULL;

UPDATE job_codes SET status = 'VACANT';

-- Codes currently held by someone still on the roster are OCCUPIED.
UPDATE job_codes jc
SET jc.status = 'OCCUPIED'
WHERE EXISTS (
    SELECT 1
    FROM employee_current_job cj
    JOIN employees e ON e.id = cj.employee_id
    WHERE cj.job_code_id = jc.id
      AND e.status NOT IN ('INACTIVE', 'TERMINATED', 'RESIGNED', 'RETIRED')
);
