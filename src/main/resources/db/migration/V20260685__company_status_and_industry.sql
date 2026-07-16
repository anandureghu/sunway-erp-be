-- Super Admin company management: a fixed industry classification, and an active/inactive
-- status so a company can be deactivated (soft-delete, matching the Customer/Vendor pattern)
-- instead of being permanently destroyed, and so deactivated companies can be blocked from
-- logging in (see AuthService.login).

DROP PROCEDURE IF EXISTS add_company_status_and_industry;
DELIMITER //
CREATE PROCEDURE add_company_status_and_industry()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'companies'
           AND COLUMN_NAME = 'industry'
    ) THEN
        ALTER TABLE companies
            ADD COLUMN industry varchar(100) DEFAULT NULL,
            ADD COLUMN is_active tinyint(1) NOT NULL DEFAULT 1;
    END IF;
END //
DELIMITER ;

CALL add_company_status_and_industry();
DROP PROCEDURE add_company_status_and_industry;
