-- Job-code holds: why the position is on hold, and how the last hold was closed
-- (approved / rejected / released, by whom, when, with a note).
ALTER TABLE job_codes
    ADD COLUMN hold_reason VARCHAR(30) NULL,
    ADD COLUMN hold_decision VARCHAR(20) NULL,
    ADD COLUMN hold_decision_note VARCHAR(500) NULL,
    ADD COLUMN hold_decided_by VARCHAR(150) NULL,
    ADD COLUMN hold_decided_at DATETIME NULL;

UPDATE job_codes SET hold_reason = 'PENDING_APPROVAL' WHERE status = 'ON_HOLD';
