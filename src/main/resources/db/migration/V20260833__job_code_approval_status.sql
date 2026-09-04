-- Job-code approval workflow: new codes start PENDING_APPROVAL and must be approved
-- by an HR manager before assignment. Existing codes are already in use, so grandfather
-- them in as APPROVED.
ALTER TABLE job_codes
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL';

UPDATE job_codes SET status = 'APPROVED';
