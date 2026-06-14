ALTER TABLE budget_headers
    ADD COLUMN budget_type VARCHAR(20) NOT NULL DEFAULT 'OPEX' AFTER fiscal_year,
    ADD COLUMN budget_account_id BIGINT NULL AFTER budget_type,
    ADD COLUMN project_id VARCHAR(255) NULL AFTER budget_account_id,
    ADD COLUMN distributed_amount DECIMAL(38, 2) NOT NULL DEFAULT 0 AFTER amount;

ALTER TABLE budget_headers
    ADD CONSTRAINT fk_budget_headers_budget_account
        FOREIGN KEY (budget_account_id) REFERENCES chart_of_accounts (id);

ALTER TABLE transactions
    ADD COLUMN archived BIT(1) NOT NULL DEFAULT 0,
    ADD COLUMN archived_at DATETIME(6) NULL,
    ADD COLUMN archived_by BIGINT NULL;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_archived_by
        FOREIGN KEY (archived_by) REFERENCES users (id);
