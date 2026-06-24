-- Journal entry archive columns (idempotent for environments where the schema
-- was patched manually before this migration was recorded in Flyway).

SET @add_archived := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `journal_entries` ADD COLUMN `archived` TINYINT(1) NOT NULL DEFAULT 0',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'journal_entries'
      AND COLUMN_NAME = 'archived'
);
PREPARE stmt FROM @add_archived;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_archived_at := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `journal_entries` ADD COLUMN `archived_at` DATETIME(6) NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'journal_entries'
      AND COLUMN_NAME = 'archived_at'
);
PREPARE stmt FROM @add_archived_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_archived_by := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `journal_entries` ADD COLUMN `archived_by` BIGINT NULL',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'journal_entries'
      AND COLUMN_NAME = 'archived_by'
);
PREPARE stmt FROM @add_archived_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_archived_by_fk := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE `journal_entries` ADD CONSTRAINT `FK_journal_entries_archived_by` FOREIGN KEY (`archived_by`) REFERENCES `users` (`id`)',
        'SELECT 1'
    )
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'journal_entries'
      AND CONSTRAINT_NAME = 'FK_journal_entries_archived_by'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
PREPARE stmt FROM @add_archived_by_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
