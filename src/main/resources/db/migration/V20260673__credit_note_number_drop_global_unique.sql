-- credit_notes already has the correct composite unique constraint
-- (credit_note_number, company_id), but a leftover global single-column
-- unique index on credit_note_number survived from before that fix and
-- silently neutralizes it (a company_id-scoped check can never violate the
-- global index cleanly, but the global index alone still causes false
-- collisions between two different companies today). Drop the leftover
-- global index; the composite one already does the job correctly.

DROP PROCEDURE IF EXISTS drop_credit_note_number_global_unique;
DELIMITER //
CREATE PROCEDURE drop_credit_note_number_global_unique()
BEGIN
    DECLARE old_idx VARCHAR(64);

    SELECT s.INDEX_NAME INTO old_idx
    FROM information_schema.STATISTICS s
    WHERE s.TABLE_SCHEMA = DATABASE()
      AND s.TABLE_NAME = 'credit_notes'
      AND s.NON_UNIQUE = 0
      AND s.INDEX_NAME <> 'PRIMARY'
    GROUP BY s.INDEX_NAME
    HAVING COUNT(*) = 1
       AND MAX(CASE WHEN s.COLUMN_NAME = 'credit_note_number' THEN 1 ELSE 0 END) = 1
    LIMIT 1;

    IF old_idx IS NOT NULL THEN
        SET @sql = CONCAT('ALTER TABLE credit_notes DROP INDEX ', old_idx);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

CALL drop_credit_note_number_global_unique();
DROP PROCEDURE drop_credit_note_number_global_unique;
