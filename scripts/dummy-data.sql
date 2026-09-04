-- Dummy data for Default Admin Company (id=1)
-- Safe to re-run only on empty inventory/sales/purchase tables; skips if warehouses already exist.
SET NAMES utf8mb4;
SET @cid := 1;
SET @admin := 1;
SET @now := NOW(6);
SET @demo_hash := '$2a$10$tfIex9Vt9lsa2ABIRUnOy./oDB/TCbCqg/BPajp3/YaeYjXB2jMea';

-- Abort if already seeded
SELECT COUNT(*) INTO @wh_count FROM warehouses WHERE company_id = @cid;
SET @skip := IF(@wh_count > 0, 1, 0);

-- ========== Roles ==========
INSERT INTO company_roles (active, created_date, description, name, updated_date, company_id)
SELECT 1, @now, 'Inventory & warehouse operations', 'Warehouse Manager', @now, @cid FROM DUAL WHERE @skip = 0
AND NOT EXISTS (SELECT 1 FROM company_roles WHERE company_id=@cid AND name='Warehouse Manager');
INSERT INTO company_roles (active, created_date, description, name, updated_date, company_id)
SELECT 1, @now, 'Purchase & procurement', 'Purchasing Officer', @now, @cid FROM DUAL WHERE @skip = 0
AND NOT EXISTS (SELECT 1 FROM company_roles WHERE company_id=@cid AND name='Purchasing Officer');
INSERT INTO company_roles (active, created_date, description, name, updated_date, company_id)
SELECT 1, @now, 'Sales operations', 'Sales Executive', @now, @cid FROM DUAL WHERE @skip = 0
AND NOT EXISTS (SELECT 1 FROM company_roles WHERE company_id=@cid AND name='Sales Executive');
INSERT INTO company_roles (active, created_date, description, name, updated_date, company_id)
SELECT 1, @now, 'Finance & accounting', 'Finance Officer', @now, @cid FROM DUAL WHERE @skip = 0
AND NOT EXISTS (SELECT 1 FROM company_roles WHERE company_id=@cid AND name='Finance Officer');
INSERT INTO company_roles (active, created_date, description, name, updated_date, company_id)
SELECT 1, @now, 'Human resources', 'HR Officer', @now, @cid FROM DUAL WHERE @skip = 0
AND NOT EXISTS (SELECT 1 FROM company_roles WHERE company_id=@cid AND name='HR Officer');

SET @role_wh := (SELECT id FROM company_roles WHERE company_id=@cid AND name='Warehouse Manager' LIMIT 1);
SET @role_po := (SELECT id FROM company_roles WHERE company_id=@cid AND name='Purchasing Officer' LIMIT 1);
SET @role_sales := (SELECT id FROM company_roles WHERE company_id=@cid AND name='Sales Executive' LIMIT 1);
SET @role_fin := (SELECT id FROM company_roles WHERE company_id=@cid AND name='Finance Officer' LIMIT 1);
SET @role_hr := (SELECT id FROM company_roles WHERE company_id=@cid AND name='HR Officer' LIMIT 1);
SET @role_emp := (SELECT id FROM company_roles WHERE company_id=@cid AND name='Employee (self service)' LIMIT 1);

-- ========== Demo users (password Demo@123) ==========
INSERT INTO users (created_at, email, force_password_reset, two_factor_enabled, full_name, password, role, username, company_id, company_role_id)
SELECT @now, 'warehouse@demo.local', 0, 0, 'Demo Warehouse', @demo_hash, 'USER', 'warehouse', @cid, @role_wh FROM DUAL WHERE @skip=0
AND NOT EXISTS (SELECT 1 FROM users WHERE username='warehouse');
INSERT INTO users (created_at, email, force_password_reset, two_factor_enabled, full_name, password, role, username, company_id, company_role_id)
SELECT @now, 'purchase@demo.local', 0, 0, 'Demo Purchaser', @demo_hash, 'USER', 'purchase', @cid, @role_po FROM DUAL WHERE @skip=0
AND NOT EXISTS (SELECT 1 FROM users WHERE username='purchase');
INSERT INTO users (created_at, email, force_password_reset, two_factor_enabled, full_name, password, role, username, company_id, company_role_id)
SELECT @now, 'sales@demo.local', 0, 0, 'Demo Sales', @demo_hash, 'USER', 'sales', @cid, @role_sales FROM DUAL WHERE @skip=0
AND NOT EXISTS (SELECT 1 FROM users WHERE username='sales');
INSERT INTO users (created_at, email, force_password_reset, two_factor_enabled, full_name, password, role, username, company_id, company_role_id)
SELECT @now, 'finance@demo.local', 0, 0, 'Demo Finance', @demo_hash, 'FINANCE_MANAGER', 'finance', @cid, @role_fin FROM DUAL WHERE @skip=0
AND NOT EXISTS (SELECT 1 FROM users WHERE username='finance');
INSERT INTO users (created_at, email, force_password_reset, two_factor_enabled, full_name, password, role, username, company_id, company_role_id)
SELECT @now, 'hr@demo.local', 0, 0, 'Demo HR', @demo_hash, 'HR', 'hrdemo', @cid, @role_hr FROM DUAL WHERE @skip=0
AND NOT EXISTS (SELECT 1 FROM users WHERE username='hrdemo');

SET @u_wh := (SELECT id FROM users WHERE username='warehouse' LIMIT 1);
SET @u_po := (SELECT id FROM users WHERE username='purchase' LIMIT 1);
SET @u_sales := (SELECT id FROM users WHERE username='sales' LIMIT 1);
SET @u_fin := (SELECT id FROM users WHERE username='finance' LIMIT 1);
SET @u_hr := (SELECT id FROM users WHERE username='hrdemo' LIMIT 1);

-- ========== Departments ==========
INSERT INTO departments (created_at, department_code, department_name, description, company_id)
SELECT @now, 'OPS', 'Operations', 'Warehouse & logistics', @cid FROM DUAL WHERE @skip=0;
INSERT INTO departments (created_at, department_code, department_name, description, company_id)
SELECT @now, 'PROC', 'Procurement', 'Purchasing', @cid FROM DUAL WHERE @skip=0;
INSERT INTO departments (created_at, department_code, department_name, description, company_id)
SELECT @now, 'SALES', 'Sales', 'Sales & CRM', @cid FROM DUAL WHERE @skip=0;
INSERT INTO departments (created_at, department_code, department_name, description, company_id)
SELECT @now, 'FIN', 'Finance', 'Finance & accounts', @cid FROM DUAL WHERE @skip=0;
INSERT INTO departments (created_at, department_code, department_name, description, company_id)
SELECT @now, 'HR', 'Human Resources', 'People ops', @cid FROM DUAL WHERE @skip=0;

SET @d_ops := (SELECT id FROM departments WHERE company_id=@cid AND department_code='OPS' LIMIT 1);
SET @d_proc := (SELECT id FROM departments WHERE company_id=@cid AND department_code='PROC' LIMIT 1);
SET @d_sales := (SELECT id FROM departments WHERE company_id=@cid AND department_code='SALES' LIMIT 1);
SET @d_fin := (SELECT id FROM departments WHERE company_id=@cid AND department_code='FIN' LIMIT 1);
SET @d_hr := (SELECT id FROM departments WHERE company_id=@cid AND department_code='HR' LIMIT 1);

-- ========== Job codes ==========
INSERT INTO job_codes (active, code, salary_grade, level, title, company_id, min_salary, max_salary, status, department_id, employment_category, employment_type, work_location, work_city, work_country)
SELECT 1, 'JC-WH-01', 'G5', 'L2', 'Warehouse Supervisor', @cid, 4000, 7000, 'APPROVED', @d_ops, 'PERMANENT', 'FULL_TIME', 'OFFICE', 'Doha', 'Qatar' FROM DUAL WHERE @skip=0;
INSERT INTO job_codes (active, code, salary_grade, level, title, company_id, min_salary, max_salary, status, department_id, employment_category, employment_type, work_location, work_city, work_country)
SELECT 1, 'JC-PO-01', 'G6', 'L3', 'Buyer', @cid, 5000, 9000, 'APPROVED', @d_proc, 'PERMANENT', 'FULL_TIME', 'OFFICE', 'Doha', 'Qatar' FROM DUAL WHERE @skip=0;
INSERT INTO job_codes (active, code, salary_grade, level, title, company_id, min_salary, max_salary, status, department_id, employment_category, employment_type, work_location, work_city, work_country)
SELECT 1, 'JC-SA-01', 'G5', 'L2', 'Sales Rep', @cid, 4500, 8000, 'APPROVED', @d_sales, 'PERMANENT', 'FULL_TIME', 'OFFICE', 'Doha', 'Qatar' FROM DUAL WHERE @skip=0;
INSERT INTO job_codes (active, code, salary_grade, level, title, company_id, min_salary, max_salary, status, department_id, employment_category, employment_type, work_location, work_city, work_country)
SELECT 1, 'JC-FN-01', 'G7', 'L3', 'Accountant', @cid, 6000, 10000, 'APPROVED', @d_fin, 'PERMANENT', 'FULL_TIME', 'OFFICE', 'Doha', 'Qatar' FROM DUAL WHERE @skip=0;
INSERT INTO job_codes (active, code, salary_grade, level, title, company_id, min_salary, max_salary, status, department_id, employment_category, employment_type, work_location, work_city, work_country)
SELECT 1, 'JC-HR-01', 'G6', 'L3', 'HR Generalist', @cid, 5500, 9500, 'APPROVED', @d_hr, 'PERMANENT', 'FULL_TIME', 'OFFICE', 'Doha', 'Qatar' FROM DUAL WHERE @skip=0;

SET @jc_wh := (SELECT id FROM job_codes WHERE company_id=@cid AND code='JC-WH-01' LIMIT 1);
SET @jc_po := (SELECT id FROM job_codes WHERE company_id=@cid AND code='JC-PO-01' LIMIT 1);
SET @jc_sa := (SELECT id FROM job_codes WHERE company_id=@cid AND code='JC-SA-01' LIMIT 1);
SET @jc_fn := (SELECT id FROM job_codes WHERE company_id=@cid AND code='JC-FN-01' LIMIT 1);
SET @jc_hr := (SELECT id FROM job_codes WHERE company_id=@cid AND code='JC-HR-01' LIMIT 1);

-- ========== Employees linked to demo users ==========
INSERT INTO employees (created_at, employee_no, first_name, last_name, gender, join_date, status, company_id, department_id, user_id, company_role_id, role, archived)
SELECT @now, 'EMP-1001', 'Aisha', 'Khan', 'FEMALE', '2024-01-15', 'ACTIVE', @cid, @d_ops, @u_wh, @role_wh, 'Warehouse Manager', 0 FROM DUAL WHERE @skip=0;
INSERT INTO employees (created_at, employee_no, first_name, last_name, gender, join_date, status, company_id, department_id, user_id, company_role_id, role, archived)
SELECT @now, 'EMP-1002', 'Omar', 'Hassan', 'MALE', '2024-02-01', 'ACTIVE', @cid, @d_proc, @u_po, @role_po, 'Purchasing Officer', 0 FROM DUAL WHERE @skip=0;
INSERT INTO employees (created_at, employee_no, first_name, last_name, gender, join_date, status, company_id, department_id, user_id, company_role_id, role, archived)
SELECT @now, 'EMP-1003', 'Sara', 'Al-Mannai', 'FEMALE', '2024-03-10', 'ACTIVE', @cid, @d_sales, @u_sales, @role_sales, 'Sales Executive', 0 FROM DUAL WHERE @skip=0;
INSERT INTO employees (created_at, employee_no, first_name, last_name, gender, join_date, status, company_id, department_id, user_id, company_role_id, role, archived)
SELECT @now, 'EMP-1004', 'James', 'Wong', 'MALE', '2023-11-01', 'ACTIVE', @cid, @d_fin, @u_fin, @role_fin, 'Finance Officer', 0 FROM DUAL WHERE @skip=0;
INSERT INTO employees (created_at, employee_no, first_name, last_name, gender, join_date, status, company_id, department_id, user_id, company_role_id, role, archived)
SELECT @now, 'EMP-1005', 'Fatima', 'Noor', 'FEMALE', '2024-04-20', 'ACTIVE', @cid, @d_hr, @u_hr, @role_hr, 'HR Officer', 0 FROM DUAL WHERE @skip=0;

SET @e_wh := (SELECT id FROM employees WHERE company_id=@cid AND employee_no='EMP-1001' LIMIT 1);
SET @e_po := (SELECT id FROM employees WHERE company_id=@cid AND employee_no='EMP-1002' LIMIT 1);
SET @e_sa := (SELECT id FROM employees WHERE company_id=@cid AND employee_no='EMP-1003' LIMIT 1);
SET @e_fn := (SELECT id FROM employees WHERE company_id=@cid AND employee_no='EMP-1004' LIMIT 1);
SET @e_hr := (SELECT id FROM employees WHERE company_id=@cid AND employee_no='EMP-1005' LIMIT 1);

UPDATE departments SET manager_id=@e_wh WHERE id=@d_ops AND @skip=0;
UPDATE departments SET manager_id=@e_po WHERE id=@d_proc AND @skip=0;
UPDATE departments SET manager_id=@e_sa WHERE id=@d_sales AND @skip=0;
UPDATE departments SET manager_id=@e_fn WHERE id=@d_fin AND @skip=0;
UPDATE departments SET manager_id=@e_hr WHERE id=@d_hr AND @skip=0;

INSERT INTO employee_current_job (effective_from, start_date, work_city, work_country, work_location, department_id, employee_id, job_code_id, employment_category, employment_type)
SELECT '2024-01-15', '2024-01-15', 'Doha', 'Qatar', 'OFFICE', @d_ops, @e_wh, @jc_wh, 'PERMANENT', 'FULL_TIME' FROM DUAL WHERE @skip=0;
INSERT INTO employee_current_job (effective_from, start_date, work_city, work_country, work_location, department_id, employee_id, job_code_id, employment_category, employment_type)
SELECT '2024-02-01', '2024-02-01', 'Doha', 'Qatar', 'OFFICE', @d_proc, @e_po, @jc_po, 'PERMANENT', 'FULL_TIME' FROM DUAL WHERE @skip=0;
INSERT INTO employee_current_job (effective_from, start_date, work_city, work_country, work_location, department_id, employee_id, job_code_id, employment_category, employment_type)
SELECT '2024-03-10', '2024-03-10', 'Doha', 'Qatar', 'OFFICE', @d_sales, @e_sa, @jc_sa, 'PERMANENT', 'FULL_TIME' FROM DUAL WHERE @skip=0;
INSERT INTO employee_current_job (effective_from, start_date, work_city, work_country, work_location, department_id, employee_id, job_code_id, employment_category, employment_type)
SELECT '2023-11-01', '2023-11-01', 'Doha', 'Qatar', 'OFFICE', @d_fin, @e_fn, @jc_fn, 'PERMANENT', 'FULL_TIME' FROM DUAL WHERE @skip=0;
INSERT INTO employee_current_job (effective_from, start_date, work_city, work_country, work_location, department_id, employee_id, job_code_id, employment_category, employment_type)
SELECT '2024-04-20', '2024-04-20', 'Doha', 'Qatar', 'OFFICE', @d_hr, @e_hr, @jc_hr, 'PERMANENT', 'FULL_TIME' FROM DUAL WHERE @skip=0;

-- ========== Chart of accounts ==========
INSERT INTO chart_of_accounts (account_code, account_name, account_no, balance, created_at, description, initial_balance_set, inter_company_number, is_active, type, year, company_id, created_by)
SELECT '1000', 'Cash on Hand', '1000001', 50000.00, @now, 'Primary cash', 1, '100', 1, 6, 2026, @cid, @admin FROM DUAL WHERE @skip=0;
INSERT INTO chart_of_accounts (account_code, account_name, account_no, balance, created_at, description, initial_balance_set, inter_company_number, is_active, type, year, company_id, created_by)
SELECT '1100', 'Accounts Receivable', '1100001', 0.00, @now, 'AR', 1, '100', 1, 0, 2026, @cid, @admin FROM DUAL WHERE @skip=0;
INSERT INTO chart_of_accounts (account_code, account_name, account_no, balance, created_at, description, initial_balance_set, inter_company_number, is_active, type, year, company_id, created_by)
SELECT '1200', 'Inventory Asset', '1200001', 0.00, @now, 'Stock asset', 1, '100', 1, 0, 2026, @cid, @admin FROM DUAL WHERE @skip=0;
INSERT INTO chart_of_accounts (account_code, account_name, account_no, balance, created_at, description, initial_balance_set, inter_company_number, is_active, type, year, company_id, created_by)
SELECT '2000', 'Accounts Payable', '2000001', 0.00, @now, 'AP', 1, '100', 1, 1, 2026, @cid, @admin FROM DUAL WHERE @skip=0;
INSERT INTO chart_of_accounts (account_code, account_name, account_no, balance, created_at, description, initial_balance_set, inter_company_number, is_active, type, year, company_id, created_by)
SELECT '4000', 'Sales Revenue', '4000001', 0.00, @now, 'Revenue', 1, '100', 1, 3, 2026, @cid, @admin FROM DUAL WHERE @skip=0;
INSERT INTO chart_of_accounts (account_code, account_name, account_no, balance, created_at, description, initial_balance_set, inter_company_number, is_active, type, year, company_id, created_by)
SELECT '5000', 'Cost of Goods Sold', '5000001', 0.00, @now, 'COGS', 1, '100', 1, 4, 2026, @cid, @admin FROM DUAL WHERE @skip=0;
INSERT INTO chart_of_accounts (account_code, account_name, account_no, balance, created_at, description, initial_balance_set, inter_company_number, is_active, type, year, company_id, created_by)
SELECT '5100', 'Purchase Expense', '5100001', 0.00, @now, 'Purchases', 1, '100', 1, 4, 2026, @cid, @admin FROM DUAL WHERE @skip=0;

SET @coa_cash := (SELECT id FROM chart_of_accounts WHERE company_id=@cid AND account_code='1000' LIMIT 1);
SET @coa_ar := (SELECT id FROM chart_of_accounts WHERE company_id=@cid AND account_code='1100' LIMIT 1);
SET @coa_inv := (SELECT id FROM chart_of_accounts WHERE company_id=@cid AND account_code='1200' LIMIT 1);
SET @coa_ap := (SELECT id FROM chart_of_accounts WHERE company_id=@cid AND account_code='2000' LIMIT 1);
SET @coa_rev := (SELECT id FROM chart_of_accounts WHERE company_id=@cid AND account_code='4000' LIMIT 1);
SET @coa_cogs := (SELECT id FROM chart_of_accounts WHERE company_id=@cid AND account_code='5000' LIMIT 1);
SET @coa_purch := (SELECT id FROM chart_of_accounts WHERE company_id=@cid AND account_code='5100' LIMIT 1);

UPDATE bank_accounts SET account_holder_name='Default Admin Company', account_number='QA-BANK-001', bank_name='Qatar National Bank', branch_name='West Bay', ifsc_code='QNBAQAQA', primary_account=1 WHERE id=1 AND @skip=0;
SET @bank := 1;

UPDATE companies SET
  default_bank_account_id = @bank,
  default_sales_credit_account_id = @coa_rev,
  default_sales_debit_account_id = @coa_ar,
  default_purchase_credit_account_id = @coa_ap,
  default_purchase_debit_account_id = @coa_purch
WHERE id=@cid AND @skip=0;

-- ========== Warehouses ==========
INSERT INTO warehouses (city, code, contact_person_name, country, created_at, name, phone, pin, status, street, company_id, created_by, manager)
SELECT 'Doha', 'WH-DOH-01', 'Aisha Khan', 'Qatar', @now, 'Doha Main Warehouse', '+974-4000-1001', '00000', 'ACTIVE', 'Industrial Area St 12', @cid, @admin, @u_wh FROM DUAL WHERE @skip=0;
INSERT INTO warehouses (city, code, contact_person_name, country, created_at, name, phone, pin, status, street, company_id, created_by, manager)
SELECT 'Doha', 'WH-DOH-02', 'Aisha Khan', 'Qatar', @now, 'Doha Cold Storage', '+974-4000-1002', '00000', 'ACTIVE', 'Food Zone Block C', @cid, @admin, @u_wh FROM DUAL WHERE @skip=0;
INSERT INTO warehouses (city, code, contact_person_name, country, created_at, name, phone, pin, status, street, company_id, created_by, manager)
SELECT 'Al Rayyan', 'WH-RAY-01', 'Omar Hassan', 'Qatar', @now, 'Rayyan Depot', '+974-4000-1003', '00000', 'ACTIVE', 'Logistics Park Gate 3', @cid, @admin, @u_wh FROM DUAL WHERE @skip=0;

SET @wh1 := (SELECT id FROM warehouses WHERE company_id=@cid AND code='WH-DOH-01' LIMIT 1);
SET @wh2 := (SELECT id FROM warehouses WHERE company_id=@cid AND code='WH-DOH-02' LIMIT 1);
SET @wh3 := (SELECT id FROM warehouses WHERE company_id=@cid AND code='WH-RAY-01' LIMIT 1);

-- ========== Categories ==========
INSERT INTO categories (code, created_at, name, status, company_id, created_by, parent_id)
SELECT 'CAT-ELEC', @now, 'Electronics', 'ACTIVE', @cid, @admin, NULL FROM DUAL WHERE @skip=0;
INSERT INTO categories (code, created_at, name, status, company_id, created_by, parent_id)
SELECT 'CAT-FOOD', @now, 'Food & Beverage', 'ACTIVE', @cid, @admin, NULL FROM DUAL WHERE @skip=0;
INSERT INTO categories (code, created_at, name, status, company_id, created_by, parent_id)
SELECT 'CAT-OFF', @now, 'Office Supplies', 'ACTIVE', @cid, @admin, NULL FROM DUAL WHERE @skip=0;

SET @cat_elec := (SELECT id FROM categories WHERE company_id=@cid AND code='CAT-ELEC' AND parent_id IS NULL LIMIT 1);
SET @cat_food := (SELECT id FROM categories WHERE company_id=@cid AND code='CAT-FOOD' AND parent_id IS NULL LIMIT 1);
SET @cat_off := (SELECT id FROM categories WHERE company_id=@cid AND code='CAT-OFF' AND parent_id IS NULL LIMIT 1);

INSERT INTO categories (code, created_at, name, status, company_id, created_by, parent_id)
SELECT 'SUB-COMP', @now, 'Computers', 'ACTIVE', @cid, @admin, @cat_elec FROM DUAL WHERE @skip=0;
INSERT INTO categories (code, created_at, name, status, company_id, created_by, parent_id)
SELECT 'SUB-ACC', @now, 'Accessories', 'ACTIVE', @cid, @admin, @cat_elec FROM DUAL WHERE @skip=0;
INSERT INTO categories (code, created_at, name, status, company_id, created_by, parent_id)
SELECT 'SUB-BEV', @now, 'Beverages', 'ACTIVE', @cid, @admin, @cat_food FROM DUAL WHERE @skip=0;
INSERT INTO categories (code, created_at, name, status, company_id, created_by, parent_id)
SELECT 'SUB-STAT', @now, 'Stationery', 'ACTIVE', @cid, @admin, @cat_off FROM DUAL WHERE @skip=0;

-- ========== Items ==========
INSERT INTO items (available, barcode, brand, category, cost_price, created_at, description, location, maximum, minimum, name, quantity, reorder_level, reserved, selling_price, list_price, sku, status, archived, sub_category, type, unit_measure, unit_sale, expiry_date, date_received, company_id, created_by, warehouse_id, negative_stock_permitted)
SELECT 45, '8901001001001', 'Dell', 'Electronics', 2800.00, @now, '14-inch business laptop', 'A-01-01', 100, 10, 'Dell Latitude 5440', 50, 15, 5, 3499.00, 3699.00, 'SKU-LAP-001', 'ACTIVE', 0, 'Computers', 'product', 'EA', 3499.00, NULL, '2025-06-01', @cid, @admin, @wh1, 0 FROM DUAL WHERE @skip=0;
INSERT INTO items (available, barcode, brand, category, cost_price, created_at, description, location, maximum, minimum, name, quantity, reorder_level, reserved, selling_price, list_price, sku, status, archived, sub_category, type, unit_measure, unit_sale, company_id, created_by, warehouse_id, negative_stock_permitted)
SELECT 120, '8901001001002', 'Logitech', 'Electronics', 85.00, @now, 'Wireless mouse', 'A-01-02', 300, 30, 'Logitech M185 Mouse', 130, 40, 10, 129.00, 149.00, 'SKU-MSE-001', 'ACTIVE', 0, 'Accessories', 'product', 'EA', 129.00, @cid, @admin, @wh1, 0 FROM DUAL WHERE @skip=0;
INSERT INTO items (available, barcode, brand, category, cost_price, created_at, description, location, maximum, minimum, name, quantity, reorder_level, reserved, selling_price, list_price, sku, status, archived, sub_category, type, unit_measure, unit_sale, expiry_date, date_received, company_id, created_by, warehouse_id, negative_stock_permitted)
SELECT 200, '8901001001003', 'Nestle', 'Food & Beverage', 2.50, @now, '500ml still water', 'C-02-01', 1000, 100, 'Pure Life Water 500ml', 220, 150, 20, 4.00, 4.50, 'SKU-WTR-001', 'ACTIVE', 0, 'Beverages', 'product', 'BOTTLE', 4.00, '2027-03-01', '2025-08-15', @cid, @admin, @wh2, 0 FROM DUAL WHERE @skip=0;
INSERT INTO items (available, barcode, brand, category, cost_price, created_at, description, location, maximum, minimum, name, quantity, reorder_level, reserved, selling_price, list_price, sku, status, archived, sub_category, type, unit_measure, unit_sale, expiry_date, date_received, company_id, created_by, warehouse_id, negative_stock_permitted)
SELECT 80, '8901001001004', 'Coca-Cola', 'Food & Beverage', 3.00, @now, '330ml can', 'C-02-02', 500, 50, 'Coca-Cola 330ml', 90, 60, 10, 5.50, 6.00, 'SKU-COLA-001', 'ACTIVE', 0, 'Beverages', 'product', 'CAN', 5.50, '2026-12-15', '2025-09-01', @cid, @admin, @wh2, 0 FROM DUAL WHERE @skip=0;
INSERT INTO items (available, barcode, brand, category, cost_price, created_at, description, location, maximum, minimum, name, quantity, reorder_level, reserved, selling_price, list_price, sku, status, archived, sub_category, type, unit_measure, unit_sale, company_id, created_by, warehouse_id, negative_stock_permitted)
SELECT 300, '8901001001005', 'Pilot', 'Office Supplies', 1.20, @now, 'Blue ballpoint pack of 10', 'B-03-01', 800, 50, 'Pilot BPS Blue Pen Pack', 320, 80, 20, 2.50, 2.99, 'SKU-PEN-001', 'ACTIVE', 0, 'Stationery', 'product', 'PACK', 2.50, @cid, @admin, @wh3, 0 FROM DUAL WHERE @skip=0;
INSERT INTO items (available, barcode, brand, category, cost_price, created_at, description, location, maximum, minimum, name, quantity, reorder_level, reserved, selling_price, list_price, sku, status, archived, sub_category, type, unit_measure, unit_sale, company_id, created_by, warehouse_id, negative_stock_permitted)
SELECT 50, '8901001001006', 'HP', 'Office Supplies', 45.00, @now, 'A4 80gsm 500 sheets', 'B-03-02', 200, 20, 'HP Copy Paper A4', 55, 25, 5, 69.00, 75.00, 'SKU-PPR-001', 'ACTIVE', 0, 'Stationery', 'product', 'REAM', 69.00, @cid, @admin, @wh3, 0 FROM DUAL WHERE @skip=0;
INSERT INTO items (available, barcode, brand, category, cost_price, created_at, description, location, maximum, minimum, name, quantity, reorder_level, reserved, selling_price, list_price, sku, status, archived, sub_category, type, unit_measure, unit_sale, company_id, created_by, warehouse_id, negative_stock_permitted)
SELECT 25, '8901001001007', 'Samsung', 'Electronics', 180.00, @now, '27-inch FHD monitor', 'A-02-01', 80, 5, 'Samsung S27C310', 28, 8, 3, 249.00, 279.00, 'SKU-MON-001', 'ACTIVE', 0, 'Accessories', 'product', 'EA', 249.00, @cid, @admin, @wh1, 0 FROM DUAL WHERE @skip=0;
INSERT INTO items (available, barcode, brand, category, cost_price, created_at, description, location, maximum, minimum, name, quantity, reorder_level, reserved, selling_price, list_price, sku, status, archived, sub_category, type, unit_measure, unit_sale, company_id, created_by, warehouse_id, negative_stock_permitted)
SELECT 15, '8901001001008', 'Generic', 'Office Supplies', 12.00, @now, 'USB-C docking hub', 'A-02-02', 60, 5, 'USB-C Multiport Hub', 18, 8, 3, 29.00, 35.00, 'SKU-HUB-001', 'ACTIVE', 0, 'Accessories', 'product', 'EA', 29.00, @cid, @admin, @wh1, 0 FROM DUAL WHERE @skip=0;

SET @i_lap := (SELECT id FROM items WHERE company_id=@cid AND sku='SKU-LAP-001' LIMIT 1);
SET @i_mse := (SELECT id FROM items WHERE company_id=@cid AND sku='SKU-MSE-001' LIMIT 1);
SET @i_wtr := (SELECT id FROM items WHERE company_id=@cid AND sku='SKU-WTR-001' LIMIT 1);
SET @i_cola := (SELECT id FROM items WHERE company_id=@cid AND sku='SKU-COLA-001' LIMIT 1);
SET @i_pen := (SELECT id FROM items WHERE company_id=@cid AND sku='SKU-PEN-001' LIMIT 1);
SET @i_ppr := (SELECT id FROM items WHERE company_id=@cid AND sku='SKU-PPR-001' LIMIT 1);
SET @i_mon := (SELECT id FROM items WHERE company_id=@cid AND sku='SKU-MON-001' LIMIT 1);
SET @i_hub := (SELECT id FROM items WHERE company_id=@cid AND sku='SKU-HUB-001' LIMIT 1);

-- Stock by warehouse
INSERT INTO item_warehouse_stock (quantity_on_hand, reserved, item_id, warehouse_id)
SELECT 50, 5, @i_lap, @wh1 FROM DUAL WHERE @skip=0;
INSERT INTO item_warehouse_stock (quantity_on_hand, reserved, item_id, warehouse_id)
SELECT 10, 0, @i_lap, @wh3 FROM DUAL WHERE @skip=0;
INSERT INTO item_warehouse_stock (quantity_on_hand, reserved, item_id, warehouse_id)
SELECT 130, 10, @i_mse, @wh1 FROM DUAL WHERE @skip=0;
INSERT INTO item_warehouse_stock (quantity_on_hand, reserved, item_id, warehouse_id)
SELECT 220, 20, @i_wtr, @wh2 FROM DUAL WHERE @skip=0;
INSERT INTO item_warehouse_stock (quantity_on_hand, reserved, item_id, warehouse_id)
SELECT 90, 10, @i_cola, @wh2 FROM DUAL WHERE @skip=0;
INSERT INTO item_warehouse_stock (quantity_on_hand, reserved, item_id, warehouse_id)
SELECT 320, 20, @i_pen, @wh3 FROM DUAL WHERE @skip=0;
INSERT INTO item_warehouse_stock (quantity_on_hand, reserved, item_id, warehouse_id)
SELECT 55, 5, @i_ppr, @wh3 FROM DUAL WHERE @skip=0;
INSERT INTO item_warehouse_stock (quantity_on_hand, reserved, item_id, warehouse_id)
SELECT 28, 3, @i_mon, @wh1 FROM DUAL WHERE @skip=0;
INSERT INTO item_warehouse_stock (quantity_on_hand, reserved, item_id, warehouse_id)
SELECT 18, 3, @i_hub, @wh1 FROM DUAL WHERE @skip=0;

-- Batches
INSERT INTO stock_batches (company_id, item_id, warehouse_id, batch_no, quantity_on_hand, unit_cost, received_at, expiry_date, source_type, created_at)
SELECT @cid, @i_lap, @wh1, 'BAT-LAP-001', 50, 2800.00, '2025-06-01', NULL, 'DIRECT_RECEIVE', @now FROM DUAL WHERE @skip=0;
INSERT INTO stock_batches (company_id, item_id, warehouse_id, batch_no, quantity_on_hand, unit_cost, received_at, expiry_date, source_type, created_at)
SELECT @cid, @i_mse, @wh1, 'BAT-MSE-001', 130, 85.00, '2025-07-10', NULL, 'DIRECT_RECEIVE', @now FROM DUAL WHERE @skip=0;
INSERT INTO stock_batches (company_id, item_id, warehouse_id, batch_no, quantity_on_hand, unit_cost, received_at, expiry_date, source_type, created_at)
SELECT @cid, @i_wtr, @wh2, 'BAT-WTR-001', 220, 2.50, '2025-08-15', '2027-03-01', 'GOODS_RECEIPT', @now FROM DUAL WHERE @skip=0;
INSERT INTO stock_batches (company_id, item_id, warehouse_id, batch_no, quantity_on_hand, unit_cost, received_at, expiry_date, source_type, created_at)
SELECT @cid, @i_cola, @wh2, 'BAT-COLA-001', 90, 3.00, '2025-09-01', '2026-12-15', 'GOODS_RECEIPT', @now FROM DUAL WHERE @skip=0;
INSERT INTO stock_batches (company_id, item_id, warehouse_id, batch_no, quantity_on_hand, unit_cost, received_at, expiry_date, source_type, created_at)
SELECT @cid, @i_pen, @wh3, 'BAT-PEN-001', 320, 1.20, '2025-05-20', NULL, 'DIRECT_RECEIVE', @now FROM DUAL WHERE @skip=0;
INSERT INTO stock_batches (company_id, item_id, warehouse_id, batch_no, quantity_on_hand, unit_cost, received_at, expiry_date, source_type, created_at)
SELECT @cid, @i_ppr, @wh3, 'BAT-PPR-001', 55, 45.00, '2025-06-15', NULL, 'DIRECT_RECEIVE', @now FROM DUAL WHERE @skip=0;
INSERT INTO stock_batches (company_id, item_id, warehouse_id, batch_no, quantity_on_hand, unit_cost, received_at, expiry_date, source_type, created_at)
SELECT @cid, @i_mon, @wh1, 'BAT-MON-001', 28, 180.00, '2025-07-01', NULL, 'DIRECT_RECEIVE', @now FROM DUAL WHERE @skip=0;
INSERT INTO stock_batches (company_id, item_id, warehouse_id, batch_no, quantity_on_hand, unit_cost, received_at, expiry_date, source_type, created_at)
SELECT @cid, @i_hub, @wh1, 'BAT-HUB-001', 18, 12.00, '2025-07-05', NULL, 'DIRECT_RECEIVE', @now FROM DUAL WHERE @skip=0;

-- ========== Vendors & Customers ==========
INSERT INTO vendor (approved, city, contact_person_name, country, created_at, credit_limit, currency_code, email, is1099vendor, is_active, payment_terms, phone_no, rejected, street, tax_id, vendor_name, website_url, company_id)
SELECT 1, 'Doha', 'Ravi Kumar', 'Qatar', @now, 100000.00, 'QAR', 'orders@techsupply.qa', 0, 1, 'NET30', '+974-4444-2001', 0, 'Tech Park Ave 4', 'TAX-V-1001', 'TechSupply Qatar', 'https://techsupply.qa', @cid FROM DUAL WHERE @skip=0;
INSERT INTO vendor (approved, city, contact_person_name, country, created_at, credit_limit, currency_code, email, is1099vendor, is_active, payment_terms, phone_no, rejected, street, tax_id, vendor_name, website_url, company_id)
SELECT 1, 'Doha', 'Maria Lopez', 'Qatar', @now, 50000.00, 'QAR', 'sales@freshbev.qa', 0, 1, 'NET15', '+974-4444-2002', 0, 'Food City Unit 8', 'TAX-V-1002', 'FreshBev Distributors', 'https://freshbev.qa', @cid FROM DUAL WHERE @skip=0;
INSERT INTO vendor (approved, city, contact_person_name, country, created_at, credit_limit, currency_code, email, is1099vendor, is_active, payment_terms, phone_no, rejected, street, tax_id, vendor_name, website_url, company_id)
SELECT 1, 'Al Rayyan', 'Chen Wei', 'Qatar', @now, 25000.00, 'QAR', 'hello@officemart.qa', 0, 1, 'NET30', '+974-4444-2003', 0, 'Office Plaza 2', 'TAX-V-1003', 'OfficeMart Trading', 'https://officemart.qa', @cid FROM DUAL WHERE @skip=0;

SET @v_tech := (SELECT id FROM vendor WHERE company_id=@cid AND vendor_name='TechSupply Qatar' LIMIT 1);
SET @v_bev := (SELECT id FROM vendor WHERE company_id=@cid AND vendor_name='FreshBev Distributors' LIMIT 1);
SET @v_off := (SELECT id FROM vendor WHERE company_id=@cid AND vendor_name='OfficeMart Trading' LIMIT 1);

INSERT INTO customers (city, contact_person_name, country, created_at, credit_limit, currency_code, customer_name, customer_type, email, is_active, payment_terms, phone_no, state, street, tax_id, website_url, company_id)
SELECT 'Doha', 'Layla Ahmad', 'Qatar', @now, 75000.00, 'QAR', 'Gulf Retail Group', 'BUSINESS', 'ap@gulfretail.qa', 1, 'NET30', '+974-5555-3001', 'Doha', 'Corniche Rd 21', 'TAX-C-2001', 'https://gulfretail.qa', @cid FROM DUAL WHERE @skip=0;
INSERT INTO customers (city, contact_person_name, country, created_at, credit_limit, currency_code, customer_name, customer_type, email, is_active, payment_terms, phone_no, state, street, tax_id, website_url, company_id)
SELECT 'Lusail', 'Tom Hardy', 'Qatar', @now, 40000.00, 'QAR', 'Lusail Hospitality Co', 'BUSINESS', 'buy@lusailhotels.qa', 1, 'NET15', '+974-5555-3002', 'Lusail', 'Marina Walk 5', 'TAX-C-2002', 'https://lusailhotels.qa', @cid FROM DUAL WHERE @skip=0;
INSERT INTO customers (city, contact_person_name, country, created_at, credit_limit, currency_code, customer_name, customer_type, email, is_active, payment_terms, phone_no, state, street, tax_id, website_url, company_id)
SELECT 'Doha', 'Nina Patel', 'Qatar', @now, 15000.00, 'QAR', 'City Cafe Chain', 'BUSINESS', 'orders@citycafe.qa', 1, 'COD', '+974-5555-3003', 'Doha', 'Souq Waqif Lane 3', 'TAX-C-2003', 'https://citycafe.qa', @cid FROM DUAL WHERE @skip=0;

SET @c_gulf := (SELECT id FROM customers WHERE company_id=@cid AND customer_name='Gulf Retail Group' LIMIT 1);
SET @c_lusail := (SELECT id FROM customers WHERE company_id=@cid AND customer_name='Lusail Hospitality Co' LIMIT 1);
SET @c_cafe := (SELECT id FROM customers WHERE company_id=@cid AND customer_name='City Cafe Chain' LIMIT 1);

-- ========== Purchase flow: PR -> PO -> GR ==========
INSERT INTO purchase_requisitions (created_at, requisition_number, status, company_id, requested_by, approved_by, approved_at, department_id, preferred_supplier_id, credit_account_id, debit_account_id, archived, requested_date, required_delivery_date, requisition_description, urgency, delivery_warehouse_id)
SELECT @now, 'PR-1001', 'APPROVED', @cid, @u_po, @admin, @now, @d_proc, @v_tech, @coa_ap, @coa_purch, 0, '2025-08-01', '2025-08-20', 'Restock laptops and mice', 'NORMAL', @wh1 FROM DUAL WHERE @skip=0;
INSERT INTO purchase_requisitions (created_at, requisition_number, status, company_id, requested_by, department_id, preferred_supplier_id, archived, requested_date, required_delivery_date, requisition_description, urgency, delivery_warehouse_id)
SELECT @now, 'PR-1002', 'SUBMITTED', @cid, @u_po, @d_proc, @v_bev, 0, '2025-09-01', '2025-09-15', 'Beverage replenishment', 'URGENT', @wh2 FROM DUAL WHERE @skip=0;

SET @pr1 := (SELECT id FROM purchase_requisitions WHERE company_id=@cid AND requisition_number='PR-1001' LIMIT 1);
SET @pr2 := (SELECT id FROM purchase_requisitions WHERE company_id=@cid AND requisition_number='PR-1002' LIMIT 1);

INSERT INTO purchase_requisition_items (remarks, requested_qty, item_id, requisition_id, estimated_unit_cost, actual_item_price)
SELECT 'Laptops for staff', 20, @i_lap, @pr1, 2800.00, 2800.00 FROM DUAL WHERE @skip=0;
INSERT INTO purchase_requisition_items (remarks, requested_qty, item_id, requisition_id, estimated_unit_cost, actual_item_price)
SELECT 'Mice', 50, @i_mse, @pr1, 85.00, 85.00 FROM DUAL WHERE @skip=0;
INSERT INTO purchase_requisition_items (remarks, requested_qty, item_id, requisition_id, estimated_unit_cost, actual_item_price)
SELECT 'Water cases', 500, @i_wtr, @pr2, 2.50, 2.50 FROM DUAL WHERE @skip=0;
INSERT INTO purchase_requisition_items (remarks, requested_qty, item_id, requisition_id, estimated_unit_cost, actual_item_price)
SELECT 'Cola cans', 300, @i_cola, @pr2, 3.00, 3.00 FROM DUAL WHERE @skip=0;

INSERT INTO purchase_orders (created_at, order_date, required_delivery_date, order_number, status, total_amount, company_id, created_by, requested_by, source_requisition_id, supplier_id, archived)
SELECT @now, '2025-08-05', '2025-08-20', 'PO-1023', 'PARTIALLY_RECEIVED', 60250.00, @cid, @u_po, @u_po, @pr1, @v_tech, 0 FROM DUAL WHERE @skip=0;
INSERT INTO purchase_orders (created_at, order_date, required_delivery_date, order_number, status, total_amount, company_id, created_by, requested_by, supplier_id, archived)
SELECT @now, '2025-08-12', '2025-08-28', 'PO-1024', 'CONFIRMED', 4125.00, @cid, @u_po, @u_po, @v_off, 0 FROM DUAL WHERE @skip=0;
INSERT INTO purchase_orders (created_at, order_date, required_delivery_date, order_number, status, total_amount, company_id, created_by, requested_by, supplier_id, archived)
SELECT @now, '2025-07-01', '2025-07-15', 'PO-1020', 'RECEIVED', 1250.00, @cid, @u_po, @u_po, @v_bev, 0 FROM DUAL WHERE @skip=0;

SET @po1023 := (SELECT id FROM purchase_orders WHERE company_id=@cid AND order_number='PO-1023' LIMIT 1);
SET @po1024 := (SELECT id FROM purchase_orders WHERE company_id=@cid AND order_number='PO-1024' LIMIT 1);
SET @po1020 := (SELECT id FROM purchase_orders WHERE company_id=@cid AND order_number='PO-1020' LIMIT 1);

INSERT INTO purchase_order_items (line_total, quantity, received_qty, rejected_qty, unit_cost, item_id, purchase_order_id, actual_item_price)
SELECT 56000.00, 20, 10, 0, 2800.00, @i_lap, @po1023, 2800.00 FROM DUAL WHERE @skip=0;
INSERT INTO purchase_order_items (line_total, quantity, received_qty, rejected_qty, unit_cost, item_id, purchase_order_id, actual_item_price)
SELECT 4250.00, 50, 50, 0, 85.00, @i_mse, @po1023, 85.00 FROM DUAL WHERE @skip=0;
INSERT INTO purchase_order_items (line_total, quantity, received_qty, rejected_qty, unit_cost, item_id, purchase_order_id, actual_item_price)
SELECT 3450.00, 50, 0, 0, 69.00, @i_ppr, @po1024, 45.00 FROM DUAL WHERE @skip=0;
INSERT INTO purchase_order_items (line_total, quantity, received_qty, rejected_qty, unit_cost, item_id, purchase_order_id, actual_item_price)
SELECT 675.00, 15, 0, 0, 45.00, @i_hub, @po1024, 12.00 FROM DUAL WHERE @skip=0;
INSERT INTO purchase_order_items (line_total, quantity, received_qty, rejected_qty, unit_cost, item_id, purchase_order_id, actual_item_price)
SELECT 1250.00, 500, 500, 0, 2.50, @i_wtr, @po1020, 2.50 FROM DUAL WHERE @skip=0;

SET @poi_lap := (SELECT id FROM purchase_order_items WHERE purchase_order_id=@po1023 AND item_id=@i_lap LIMIT 1);
SET @poi_mse := (SELECT id FROM purchase_order_items WHERE purchase_order_id=@po1023 AND item_id=@i_mse LIMIT 1);
SET @poi_wtr := (SELECT id FROM purchase_order_items WHERE purchase_order_id=@po1020 AND item_id=@i_wtr LIMIT 1);
SET @poi_ppr := (SELECT id FROM purchase_order_items WHERE purchase_order_id=@po1024 AND item_id=@i_ppr LIMIT 1);

INSERT INTO goods_receipts (received_at, company_id, purchase_order_id, status, received_by, archived)
SELECT @now, @cid, @po1023, 'PENDING_INSPECTION', @u_wh, 0 FROM DUAL WHERE @skip=0;
INSERT INTO goods_receipts (received_at, company_id, purchase_order_id, status, received_by, inspected_by, inspected_at, archived)
SELECT '2025-07-16 10:00:00', @cid, @po1020, 'INSPECTED', @u_wh, @admin, '2025-07-16 12:00:00', 0 FROM DUAL WHERE @skip=0;
INSERT INTO goods_receipts (received_at, company_id, purchase_order_id, status, received_by, archived)
SELECT @now, @cid, @po1024, 'PENDING_INSPECTION', @u_wh, 0 FROM DUAL WHERE @skip=0;

SET @gr1 := (SELECT id FROM goods_receipts WHERE purchase_order_id=@po1023 ORDER BY id DESC LIMIT 1);
SET @gr2 := (SELECT id FROM goods_receipts WHERE purchase_order_id=@po1020 ORDER BY id DESC LIMIT 1);
SET @gr3 := (SELECT id FROM goods_receipts WHERE purchase_order_id=@po1024 ORDER BY id DESC LIMIT 1);

INSERT INTO goods_receipt_items (accepted_qty, received_qty, ordered_quantity, rejected_qty, remarks, item_id, purchase_order_item_id, goods_receipt_id, warehouse_id, batch_no, unit_cost)
SELECT 10, 10, 20, 0, 'Partial laptop receipt', @i_lap, @poi_lap, @gr1, @wh1, 'GR-LAP-1023', 2800.00 FROM DUAL WHERE @skip=0;
INSERT INTO goods_receipt_items (accepted_qty, received_qty, ordered_quantity, rejected_qty, remarks, item_id, purchase_order_item_id, goods_receipt_id, warehouse_id, batch_no, unit_cost, stocked_at)
SELECT 50, 50, 50, 0, 'Mice fully received', @i_mse, @poi_mse, @gr1, @wh1, 'GR-MSE-1023', 85.00, @now FROM DUAL WHERE @skip=0;
INSERT INTO goods_receipt_items (accepted_qty, received_qty, ordered_quantity, rejected_qty, remarks, item_id, purchase_order_item_id, goods_receipt_id, warehouse_id, batch_no, unit_cost, stocked_at)
SELECT 500, 500, 500, 0, 'Water received & stocked', @i_wtr, @poi_wtr, @gr2, @wh2, 'BAT-WTR-001', 2.50, '2025-07-16 12:30:00' FROM DUAL WHERE @skip=0;
INSERT INTO goods_receipt_items (accepted_qty, received_qty, ordered_quantity, rejected_qty, remarks, item_id, purchase_order_item_id, goods_receipt_id, warehouse_id, batch_no, unit_cost)
SELECT NULL, NULL, 50, NULL, 'Awaiting receipt', @i_ppr, @poi_ppr, @gr3, @wh3, NULL, 45.00 FROM DUAL WHERE @skip=0;

-- ========== Sales orders + invoice ==========
INSERT INTO sales_orders (created_at, order_date, order_number, status, total_amount, company_id, created_by, customer_id, invoice_due_date, bank_account_id, credit_account_id, debit_account_id, discount_amount, subtotal_amount, tax_amount, shipping_address, archived)
SELECT @now, '2025-09-01', 'SO-2001', 'CONFIRMED', 7377.90, @cid, @u_sales, @c_gulf, '2025-10-01', @bank, @coa_rev, @coa_ar, 0.00, 6999.00, 378.90, 'Corniche Rd 21, Doha', 0 FROM DUAL WHERE @skip=0;
INSERT INTO sales_orders (created_at, order_date, order_number, status, total_amount, company_id, created_by, customer_id, invoice_due_date, bank_account_id, credit_account_id, debit_account_id, discount_amount, subtotal_amount, tax_amount, shipping_address, archived)
SELECT @now, '2025-09-02', 'SO-2002', 'QUOTATION', 440.00, @cid, @u_sales, @c_cafe, '2025-09-16', @bank, @coa_rev, @coa_ar, 0.00, 400.00, 40.00, 'Souq Waqif Lane 3, Doha', 0 FROM DUAL WHERE @skip=0;
INSERT INTO sales_orders (created_at, order_date, order_number, status, total_amount, company_id, created_by, customer_id, invoice_due_date, bank_account_id, credit_account_id, debit_account_id, discount_amount, subtotal_amount, tax_amount, shipping_address, archived)
SELECT @now, '2025-08-20', 'SO-2000', 'COMPLETED', 274.50, @cid, @u_sales, @c_lusail, '2025-09-05', @bank, @coa_rev, @coa_ar, 0.00, 249.00, 25.50, 'Marina Walk 5, Lusail', 0 FROM DUAL WHERE @skip=0;

SET @so1 := (SELECT id FROM sales_orders WHERE company_id=@cid AND order_number='SO-2001' LIMIT 1);
SET @so2 := (SELECT id FROM sales_orders WHERE company_id=@cid AND order_number='SO-2002' LIMIT 1);
SET @so3 := (SELECT id FROM sales_orders WHERE company_id=@cid AND order_number='SO-2000' LIMIT 1);

INSERT INTO sales_order_items (line_total, quantity, returned_qty, unit_price, item_id, sales_order_id, discount_percent, line_subtotal, tax_amount, tax_rate, warehouse_id)
SELECT 3499.00, 1, 0, 3499.00, @i_lap, @so1, 0.00, 3499.00, 174.95, 5.00, @wh1 FROM DUAL WHERE @skip=0;
INSERT INTO sales_order_items (line_total, quantity, returned_qty, unit_price, item_id, sales_order_id, discount_percent, line_subtotal, tax_amount, tax_rate, warehouse_id)
SELECT 2490.00, 10, 0, 249.00, @i_mon, @so1, 0.00, 2490.00, 124.50, 5.00, @wh1 FROM DUAL WHERE @skip=0;
INSERT INTO sales_order_items (line_total, quantity, returned_qty, unit_price, item_id, sales_order_id, discount_percent, line_subtotal, tax_amount, tax_rate, warehouse_id)
SELECT 1010.00, 10, 0, 4.00, @i_wtr, @so1, 0.00, 40.00, 2.00, 5.00, @wh2 FROM DUAL WHERE @skip=0;
INSERT INTO sales_order_items (line_total, quantity, returned_qty, unit_price, item_id, sales_order_id, discount_percent, line_subtotal, tax_amount, tax_rate, warehouse_id)
SELECT 400.00, 100, 0, 4.00, @i_wtr, @so2, 0.00, 400.00, 40.00, 10.00, @wh2 FROM DUAL WHERE @skip=0;
INSERT INTO sales_order_items (line_total, quantity, returned_qty, unit_price, item_id, sales_order_id, discount_percent, line_subtotal, tax_amount, tax_rate, warehouse_id)
SELECT 249.00, 1, 0, 249.00, @i_mon, @so3, 0.00, 249.00, 12.45, 5.00, @wh1 FROM DUAL WHERE @skip=0;

INSERT INTO invoices (amount, created_at, due_date, invoice_date, invoice_id, item_description, open_amount, order_id, outstanding, status, to_party, type, company_id, credit_account, debit_account, bank_account_id, discount_amount, subtotal_amount, tax_amount, document_source, archived)
SELECT 274.50, @now, '2025-09-05', '2025-08-20', 'INV-SO-2000', 'Monitor sale', 0.00, @so3, 0.00, 'PAID', 'Lusail Hospitality Co', 0, @cid, @coa_rev, @coa_ar, @bank, 0.00, 249.00, 25.50, 'GENERATED', 0 FROM DUAL WHERE @skip=0;
INSERT INTO invoices (amount, created_at, due_date, invoice_date, invoice_id, item_description, open_amount, order_id, outstanding, status, to_party, type, company_id, credit_account, debit_account, bank_account_id, discount_amount, subtotal_amount, tax_amount, document_source, archived)
SELECT 7377.90, @now, '2025-10-01', '2025-09-01', 'INV-SO-2001', 'Laptop + monitors + water', 7377.90, @so1, 7377.90, 'UNPAID', 'Gulf Retail Group', 0, @cid, @coa_rev, @coa_ar, @bank, 0.00, 6999.00, 378.90, 'GENERATED', 0 FROM DUAL WHERE @skip=0;
INSERT INTO invoices (amount, created_at, due_date, invoice_date, invoice_id, item_description, open_amount, order_id, outstanding, status, to_party, type, company_id, credit_account, debit_account, bank_account_id, discount_amount, subtotal_amount, tax_amount, document_source, archived)
SELECT 60250.00, @now, '2025-09-05', '2025-08-05', 'INV-PO-1023', 'TechSupply PO-1023', 60250.00, @po1023, 60250.00, 'UNPAID', 'TechSupply Qatar', 1, @cid, @coa_ap, @coa_purch, @bank, 0.00, 60250.00, 0.00, 'SUPPLIER_UPLOAD', 0 FROM DUAL WHERE @skip=0;

SELECT IF(@skip=1, 'SKIPPED: warehouses already present', 'SEEDED OK') AS result;
SELECT 'warehouses' t, COUNT(*) c FROM warehouses WHERE company_id=1
UNION ALL SELECT 'items', COUNT(*) FROM items WHERE company_id=1
UNION ALL SELECT 'vendors', COUNT(*) FROM vendor WHERE company_id=1
UNION ALL SELECT 'customers', COUNT(*) FROM customers WHERE company_id=1
UNION ALL SELECT 'POs', COUNT(*) FROM purchase_orders WHERE company_id=1
UNION ALL SELECT 'SOs', COUNT(*) FROM sales_orders WHERE company_id=1
UNION ALL SELECT 'users', COUNT(*) FROM users WHERE company_id=1;
