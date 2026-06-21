ALTER TABLE journal_entries
    ADD COLUMN archived TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN archived_at DATETIME(6) NULL,
    ADD COLUMN archived_by BIGINT NULL,
    ADD CONSTRAINT FK_journal_entries_archived_by
        FOREIGN KEY (archived_by) REFERENCES users (id);
