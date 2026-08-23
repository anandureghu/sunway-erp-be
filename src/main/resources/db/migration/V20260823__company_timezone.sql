-- Company attendance / display timezone (IANA). Default Qatar.
SET @db := DATABASE();

SET @sql := (
  SELECT IF(
    EXISTS(
      SELECT 1 FROM information_schema.columns
      WHERE table_schema = @db
        AND table_name = 'companies'
        AND column_name = 'timezone'
    ),
    'SELECT 1',
    'ALTER TABLE companies ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT ''Asia/Qatar'''
  )
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
