CREATE TABLE IF NOT EXISTS knowledge_base_items (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200)  NOT NULL,
    description     TEXT          NULL,
    content_type    VARCHAR(20)   NOT NULL,
    file_name       VARCHAR(255)  NOT NULL,
    content_type_mime VARCHAR(120) NULL,
    size_bytes      BIGINT        NOT NULL DEFAULT 0,
    blob_path       VARCHAR(500)  NOT NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
