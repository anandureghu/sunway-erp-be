-- Per-company configuration for all auto-generated document numbers.
-- docType is the internal key (EMP, PO, PR, INV, etc.).
-- prefix is the display prefix used in the generated number.
-- start_number seeds the sequence when it is first created.
CREATE TABLE company_numbering_configs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id    BIGINT       NOT NULL,
    doc_type      VARCHAR(30)  NOT NULL,
    prefix        VARCHAR(20)           DEFAULT NULL,
    start_number  BIGINT       NOT NULL DEFAULT 1000,
    UNIQUE KEY uk_company_doc_type (company_id, doc_type),
    CONSTRAINT fk_numbering_company
        FOREIGN KEY (company_id) REFERENCES companies(id) ON DELETE CASCADE
);
