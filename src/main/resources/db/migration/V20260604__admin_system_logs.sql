CREATE TABLE admin_system_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at      TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    level           VARCHAR(16)  NOT NULL,
    module          VARCHAR(64)  NOT NULL,
    logger_name     VARCHAR(255) NULL,
    message         VARCHAR(4000) NOT NULL,
    stack_trace     TEXT NULL,
    user_id         BIGINT NULL,
    user_email      VARCHAR(255) NULL,
    user_username   VARCHAR(255) NULL,
    company_id      BIGINT NULL,
    request_method  VARCHAR(16) NULL,
    request_uri     VARCHAR(512) NULL,
    INDEX idx_admin_system_logs_created_at (created_at DESC),
    INDEX idx_admin_system_logs_level (level),
    INDEX idx_admin_system_logs_module (module),
    INDEX idx_admin_system_logs_user_id (user_id)
);
