CREATE TABLE platform_settings (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    street    VARCHAR(50),
    city      VARCHAR(50),
    state     VARCHAR(50),
    country   VARCHAR(50),
    bank_name VARCHAR(100),
    iban      VARCHAR(64)
);
