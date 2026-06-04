-- Leave delegation: the colleague (same department) who covers the requestor's
-- responsibilities during the leave period. Optional, hence nullable.
ALTER TABLE employee_leaves
    ADD COLUMN delegate_employee_id bigint NULL,
    ADD CONSTRAINT fk_leave_delegate
        FOREIGN KEY (delegate_employee_id) REFERENCES employees (id);
