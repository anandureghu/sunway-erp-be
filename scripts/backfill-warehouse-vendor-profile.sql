-- Idempotent enrichment after V20260906 (warehouse type/capacity + vendor profile).
-- Safe to re-run. Does not alter schema.

-- Warehouse type/capacity from existing codes/names
UPDATE warehouses
SET warehouse_type = CASE
    WHEN code LIKE '%COLD%' OR LOWER(name) LIKE '%cold%' THEN 'COLD_STORAGE'
    WHEN LOWER(name) LIKE '%depot%' OR LOWER(name) LIKE '%branch%' THEN 'BRANCH'
    WHEN LOWER(name) LIKE '%transit%' THEN 'TRANSIT'
    WHEN LOWER(name) LIKE '%return%' THEN 'RETURNS'
    ELSE 'MAIN'
END
WHERE warehouse_type IS NULL OR warehouse_type = '';

UPDATE warehouses
SET capacity = CASE
    WHEN warehouse_type = 'COLD_STORAGE' THEN 5000
    WHEN warehouse_type = 'BRANCH' THEN 2500
    WHEN warehouse_type = 'TRANSIT' THEN 1000
    ELSE 10000
END
WHERE capacity IS NULL;

-- Vendor category mapping (top-level item categories)
UPDATE vendor v
INNER JOIN categories c
  ON c.company_id = v.company_id
 AND c.parent_id IS NULL
 AND c.code = 'CAT-ELEC'
SET v.category_id = c.id
WHERE v.category_id IS NULL
  AND LOWER(v.vendor_name) LIKE '%tech%';

UPDATE vendor v
INNER JOIN categories c
  ON c.company_id = v.company_id
 AND c.parent_id IS NULL
 AND c.code = 'CAT-FOOD'
SET v.category_id = c.id
WHERE v.category_id IS NULL
  AND (LOWER(v.vendor_name) LIKE '%bev%' OR LOWER(v.vendor_name) LIKE '%food%');

UPDATE vendor v
INNER JOIN categories c
  ON c.company_id = v.company_id
 AND c.parent_id IS NULL
 AND c.code = 'CAT-OFF'
SET v.category_id = c.id
WHERE v.category_id IS NULL
  AND (LOWER(v.vendor_name) LIKE '%office%' OR LOWER(v.vendor_name) LIKE '%mart%');

UPDATE vendor v
INNER JOIN (
  SELECT company_id, MIN(id) AS category_id
  FROM categories
  WHERE parent_id IS NULL
  GROUP BY company_id
) first_cat ON first_cat.company_id = v.company_id
SET v.category_id = first_cat.category_id
WHERE v.category_id IS NULL;

-- Vendor commercial / bank defaults
UPDATE vendor
SET vendor_cr_no = CONCAT('CR-', LPAD(id, 5, '0'))
WHERE vendor_cr_no IS NULL OR vendor_cr_no = '';

UPDATE vendor
SET bank_name = 'Qatar National Bank'
WHERE bank_name IS NULL OR bank_name = '';

UPDATE vendor
SET iban = CONCAT('QA58QNBA000000000000000000', LPAD(id, 3, '0'))
WHERE iban IS NULL OR iban = '';

-- Ensure vendor_code exists (migration should have done this; keep safe)
UPDATE vendor
SET vendor_code = CONCAT('SUP-', id)
WHERE vendor_code IS NULL OR vendor_code = '';

-- Document sequences for auto WH-/SUP- issuance
INSERT INTO document_sequence (document_type, next_value, version)
SELECT CONCAT(c.id, '_WH'), 1000, 0
FROM companies c
WHERE NOT EXISTS (
  SELECT 1 FROM document_sequence ds WHERE ds.document_type = CONCAT(c.id, '_WH')
);

INSERT INTO document_sequence (document_type, next_value, version)
SELECT CONCAT(c.id, '_SUP'), 1000, 0
FROM companies c
WHERE NOT EXISTS (
  SELECT 1 FROM document_sequence ds WHERE ds.document_type = CONCAT(c.id, '_SUP')
);

UPDATE document_sequence ds
JOIN (
  SELECT company_id,
         GREATEST(
           1000,
           COALESCE(MAX(CAST(SUBSTRING_INDEX(vendor_code, '-', -1) AS UNSIGNED)) + 1, 1000)
         ) AS next_sup
  FROM vendor
  WHERE vendor_code REGEXP '^SUP-[0-9]+$'
  GROUP BY company_id
) x ON ds.document_type = CONCAT(x.company_id, '_SUP')
SET ds.next_value = GREATEST(IFNULL(ds.next_value, 0), x.next_sup);

UPDATE document_sequence ds
JOIN (
  SELECT company_id,
         GREATEST(
           1000,
           COALESCE(MAX(CAST(SUBSTRING_INDEX(code, '-', -1) AS UNSIGNED)) + 1, 1000)
         ) AS next_wh
  FROM warehouses
  WHERE code REGEXP '^WH-[0-9]+$'
  GROUP BY company_id
) x ON ds.document_type = CONCAT(x.company_id, '_WH')
SET ds.next_value = GREATEST(IFNULL(ds.next_value, 0), x.next_wh);
