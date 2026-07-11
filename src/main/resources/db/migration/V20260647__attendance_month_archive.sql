-- Persistent snapshot of a month's per-employee worked days / hours. HR archives
-- a completed month (typically once payroll is generated) so the worked-day
-- figures that fed payroll stay auditable even if raw timesheets change later.
CREATE TABLE IF NOT EXISTS `attendance_month_archive` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `company_id` bigint NOT NULL,
    `employee_id` bigint NOT NULL,
    `employee_no` varchar(64) DEFAULT NULL,
    `employee_name` varchar(255) DEFAULT NULL,
    `department` varchar(255) DEFAULT NULL,
    `period_year` int NOT NULL,
    `period_month` int NOT NULL,
    `days_recorded` int NOT NULL DEFAULT 0,
    `days_present` int NOT NULL DEFAULT 0,
    `total_hours` double NOT NULL DEFAULT 0,
    `archived_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `archived_by` bigint DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_attendance_month_archive` (`company_id`, `employee_id`, `period_year`, `period_month`),
    KEY `idx_attendance_archive_company_period` (`company_id`, `period_year`, `period_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
