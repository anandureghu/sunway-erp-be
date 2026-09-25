-- Repair schema drift for company_numbering_configs if an older partial migration
-- left the Flyway history in a success state without the actual MySQL table.
CREATE TABLE IF NOT EXISTS company_numbering_configs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    doc_type      VARCHAR(30)  NOT NULL,
    prefix        VARCHAR(20)           DEFAULT NULL,
    start_number  BIGINT       NOT NULL DEFAULT 1000,
    UNIQUE KEY uk_company_doc_type (company_id, doc_type),
    CONSTRAINT fk_numbering_company
        FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
) ENGINE=InnoDB;
