-- Optional supplier on stock batches (set when receiving into stock).
ALTER TABLE stock_batches
    ADD COLUMN supplier_id BIGINT NULL;

ALTER TABLE stock_batches
    ADD CONSTRAINT fk_stock_batches_supplier
        FOREIGN KEY (supplier_id) REFERENCES vendor (id);
