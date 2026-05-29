CREATE TABLE IF NOT EXISTS purchase_requisition_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requisition_id BIGINT NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    blob_path VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255) DEFAULT NULL,
    file_size_bytes BIGINT DEFAULT NULL,
    uploaded_at DATETIME(6) DEFAULT NULL,
    uploaded_by BIGINT DEFAULT NULL,
    PRIMARY KEY (id),
    KEY FK_pr_doc_requisition (requisition_id),
    KEY FK_pr_doc_uploaded_by (uploaded_by),
    CONSTRAINT FK_pr_doc_requisition
        FOREIGN KEY (requisition_id) REFERENCES purchase_requisitions (id) ON DELETE CASCADE,
    CONSTRAINT FK_pr_doc_uploaded_by
        FOREIGN KEY (uploaded_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
