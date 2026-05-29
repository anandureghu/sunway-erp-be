ALTER TABLE purchase_requisitions
    ADD COLUMN rejection_reason VARCHAR(2000) NULL,
    ADD COLUMN review_action VARCHAR(20) NULL,
    ADD COLUMN rejected_at DATETIME(6) NULL,
    ADD COLUMN rejected_by BIGINT NULL,
    ADD CONSTRAINT FK_pr_rejected_by FOREIGN KEY (rejected_by) REFERENCES users (id);
