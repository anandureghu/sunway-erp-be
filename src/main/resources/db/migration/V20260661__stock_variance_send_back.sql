-- Adds a "send back" step to stock variance approval, distinct from a terminal
-- rejection: an approver can return a pending variance to its requester with a
-- reason, and the requester can revise and resubmit it as a fresh pending request.

DROP PROCEDURE IF EXISTS add_stock_variance_send_back;
DELIMITER //
CREATE PROCEDURE add_stock_variance_send_back()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'stock_variances'
           AND COLUMN_NAME = 'sent_back_by'
    ) THEN
        ALTER TABLE stock_variances
            ADD COLUMN sent_back_by bigint DEFAULT NULL AFTER rejected_at,
            ADD COLUMN sent_back_at datetime(6) DEFAULT NULL AFTER sent_back_by,
            ADD COLUMN sent_back_reason varchar(500) DEFAULT NULL AFTER sent_back_at;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'stock_variances'
           AND CONSTRAINT_NAME = 'fk_stock_variances_sent_back_by'
    ) THEN
        ALTER TABLE stock_variances
            ADD CONSTRAINT fk_stock_variances_sent_back_by
            FOREIGN KEY (sent_back_by) REFERENCES users (id);
    END IF;
END //
DELIMITER ;

CALL add_stock_variance_send_back();
DROP PROCEDURE add_stock_variance_send_back;
