-- invoice_id (e.g. "INV-1000") was globally unique across all companies, but
-- DocumentSequenceService resets its counter per company, so two companies
-- can legitimately land on the same invoice_id. Scope the uniqueness to the
-- company, dropping whichever single-column unique index currently exists on
-- the column (name varies by environment / Hibernate version).

DROP PROCEDURE IF EXISTS scope_invoice_id_uniqueness_to_company;
DELIMITER //
CREATE PROCEDURE scope_invoice_id_uniqueness_to_company()
BEGIN
    DECLARE old_idx VARCHAR(64);

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'invoices'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'invoice_id' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE invoices DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'invoices'
           AND INDEX_NAME = 'uk_invoices_company_invoice_id'
    ) THEN
        ALTER TABLE invoices
            ADD CONSTRAINT uk_invoices_company_invoice_id
            UNIQUE (company_id, invoice_id);
    END IF;
END //
DELIMITER ;

CALL scope_invoice_id_uniqueness_to_company();
DROP PROCEDURE scope_invoice_id_uniqueness_to_company;
