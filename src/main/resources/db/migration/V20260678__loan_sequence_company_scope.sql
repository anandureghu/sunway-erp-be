-- loan_sequence had ONE global counter per loan_type shared by every tenant
-- (no company_id at all) — every company's loan codes were interleaved from
-- the same counter. Scope it per company. Existing rows are a derived
-- "next number" counter, not user data, and can't be attributed to any one
-- company retroactively, so they are cleared rather than backfilled; each
-- company will lazily get its own fresh counter on next use (same pattern as
-- DocumentSequenceService).

DROP PROCEDURE IF EXISTS migrate_loan_sequence_company_scope;
DELIMITER //
CREATE PROCEDURE migrate_loan_sequence_company_scope()
BEGIN
    DECLARE old_idx VARCHAR(64);

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'loan_sequence'
           AND COLUMN_NAME = 'company_id'
    ) THEN
        ALTER TABLE loan_sequence ADD COLUMN company_id BIGINT NULL;
        DELETE FROM loan_sequence WHERE company_id IS NULL;
    END IF;

    ALTER TABLE loan_sequence MODIFY COLUMN company_id BIGINT NOT NULL;

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'loan_sequence'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'loan_type' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE loan_sequence DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'loan_sequence'
           AND INDEX_NAME = 'uk_loan_sequence_company_loan_type'
    ) THEN
        ALTER TABLE loan_sequence
            ADD CONSTRAINT uk_loan_sequence_company_loan_type
            UNIQUE (company_id, loan_type);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.KEY_COLUMN_USAGE
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'loan_sequence'
           AND COLUMN_NAME = 'company_id'
           AND REFERENCED_TABLE_NAME = 'companies'
    ) THEN
        ALTER TABLE loan_sequence
            ADD CONSTRAINT fk_loan_sequence_company
            FOREIGN KEY (company_id) REFERENCES companies(id);
    END IF;
END //
DELIMITER ;

CALL migrate_loan_sequence_company_scope();
DROP PROCEDURE migrate_loan_sequence_company_scope;
