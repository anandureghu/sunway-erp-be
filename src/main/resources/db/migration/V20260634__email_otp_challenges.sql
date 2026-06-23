CREATE TABLE email_otp_challenges (
    id                           BIGINT AUTO_INCREMENT PRIMARY KEY,
    email                        VARCHAR(255) NOT NULL,
    purpose                      VARCHAR(64)  NOT NULL,
    code_hash                    VARCHAR(255) NOT NULL,
    attempt_count                INT          NOT NULL DEFAULT 0,
    expires_at                   DATETIME(6)  NOT NULL,
    verified_at                  DATETIME(6)  NULL,
    verification_token           VARCHAR(64)  NULL,
    verification_token_expires_at DATETIME(6) NULL,
    created_at                   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_email_otp_email_purpose (email, purpose),
    INDEX idx_email_otp_verification_token (verification_token),
    INDEX idx_email_otp_expires_at (expires_at)
);
