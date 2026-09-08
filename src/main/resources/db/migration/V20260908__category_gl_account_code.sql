-- Optional GL account code on categories / sub-categories
ALTER TABLE categories
    ADD COLUMN gl_account_code VARCHAR(64) NULL;
