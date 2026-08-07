-- Customer returns: track returned qty on SO lines and audit sales_returns.

DROP PROCEDURE IF EXISTS add_sales_return_support;
DELIMITER //
CREATE PROCEDURE add_sales_return_support()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sales_order_items'
           AND COLUMN_NAME = 'returned_qty'
    ) THEN
        ALTER TABLE sales_order_items
            ADD COLUMN returned_qty INT NOT NULL DEFAULT 0 AFTER quantity;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sales_returns'
    ) THEN
        CREATE TABLE sales_returns (
            id BIGINT NOT NULL AUTO_INCREMENT,
            return_number VARCHAR(64) NOT NULL,
            sales_order_id BIGINT NOT NULL,
            company_id BIGINT NOT NULL,
            credit_note_id BIGINT NULL,
            total_amount DECIMAL(18, 2) NOT NULL,
            reason VARCHAR(1000) NULL,
            restock TINYINT(1) NOT NULL DEFAULT 1,
            status VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
            created_by BIGINT NULL,
            created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
            PRIMARY KEY (id),
            UNIQUE KEY uk_sales_returns_company_number (company_id, return_number),
            CONSTRAINT fk_sales_returns_so FOREIGN KEY (sales_order_id) REFERENCES sales_orders (id),
            CONSTRAINT fk_sales_returns_company FOREIGN KEY (company_id) REFERENCES companies (id),
            CONSTRAINT fk_sales_returns_cn FOREIGN KEY (credit_note_id) REFERENCES credit_notes (id)
        );

        CREATE TABLE sales_return_items (
            id BIGINT NOT NULL AUTO_INCREMENT,
            sales_return_id BIGINT NOT NULL,
            sales_order_item_id BIGINT NOT NULL,
            item_id BIGINT NOT NULL,
            warehouse_id BIGINT NULL,
            quantity INT NOT NULL,
            unit_price DECIMAL(18, 2) NOT NULL,
            line_total DECIMAL(18, 2) NOT NULL,
            PRIMARY KEY (id),
            CONSTRAINT fk_sri_return FOREIGN KEY (sales_return_id) REFERENCES sales_returns (id) ON DELETE CASCADE,
            CONSTRAINT fk_sri_soi FOREIGN KEY (sales_order_item_id) REFERENCES sales_order_items (id),
            CONSTRAINT fk_sri_item FOREIGN KEY (item_id) REFERENCES items (id)
        );
    END IF;
END //
DELIMITER ;

CALL add_sales_return_support();
DROP PROCEDURE add_sales_return_support;
