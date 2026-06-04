-- Final-settlement payroll for exiting employees (TERMINATED / RESIGNED / RETIRED).
--   * end_of_service_compensation: accrued gratuity paid out on the final run.
--   * final_settlement: marks the run as an exit settlement (gratuity paid,
--     active loans recovered in full from it).
ALTER TABLE payroll
    ADD COLUMN end_of_service_compensation double NOT NULL DEFAULT 0,
    ADD COLUMN final_settlement bit(1) NOT NULL DEFAULT 0;
