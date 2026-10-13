-- Company-scoped CRM enquiries (prospects) — separate from platform admin enquiries.

CREATE TABLE IF NOT EXISTS crm_enquiries (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    name                VARCHAR(150) NOT NULL,
    organization_name   VARCHAR(200) NULL,
    prospect_type       VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    email               VARCHAR(255) NULL,
    phone               VARCHAR(40)  NULL,
    secondary_contact   VARCHAR(200) NULL,
    address             VARCHAR(500) NULL,
    source              VARCHAR(100) NULL,
    interest            VARCHAR(200) NULL,
    summary             TEXT         NULL,
    pipeline_status     VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    relationship_stage  VARCHAR(20)  NOT NULL DEFAULT 'PROSPECTIVE',
    customer_id         BIGINT       NULL,
    vendor_id           BIGINT       NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_crm_enquiries_company
        FOREIGN KEY (company_id) REFERENCES companies (id),
    INDEX idx_crm_enquiries_company (company_id),
    INDEX idx_crm_enquiries_pipeline (company_id, pipeline_status),
    INDEX idx_crm_enquiries_stage (company_id, relationship_stage)
);

CREATE TABLE IF NOT EXISTS crm_enquiry_notes (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    enquiry_id    BIGINT       NOT NULL,
    body          TEXT         NOT NULL,
    created_by    VARCHAR(120) NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_crm_enquiry_notes_enquiry
        FOREIGN KEY (enquiry_id) REFERENCES crm_enquiries (id) ON DELETE CASCADE,
    INDEX idx_crm_enquiry_notes_enquiry (enquiry_id)
);
