ALTER TABLE users
    ADD COLUMN two_factor_enabled bit(1) NOT NULL DEFAULT b'0' AFTER force_password_reset;
