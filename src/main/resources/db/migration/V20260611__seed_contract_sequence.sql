-- The Contract entity uses GenerationType.SEQUENCE (sequenceName = contract_sequence).
-- MySQL has no real sequences, so Hibernate emulates it with the contract_sequence
-- table. Under ddl-auto=validate the seed row is never created automatically, so the
-- first insert fails with "could not read a hi value - you need to populate the table:
-- contract_sequence". Seed a single starting row (idempotent).
INSERT INTO contract_sequence (next_val)
SELECT 1 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM contract_sequence);
