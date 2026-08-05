-- Qatar labor-law company defaults: OT rates, wage/allowance floors.
-- Leave policy upsert + balance sync runs once via QatarLaborLawDefaultsInitializer.

CREATE TABLE IF NOT EXISTS one_time_tasks (
    task_key     VARCHAR(100) PRIMARY KEY,
    completed_at TIMESTAMP NULL
);

ALTER TABLE companies
    ADD COLUMN ot_day_rate_multiplier DECIMAL(6, 2) NOT NULL DEFAULT 1.25,
    ADD COLUMN ot_night_friday_holiday_rate_multiplier DECIMAL(6, 2) NOT NULL DEFAULT 1.50,
    ADD COLUMN ot_night_start_time TIME NOT NULL DEFAULT '21:00:00',
    ADD COLUMN ot_night_end_time TIME NOT NULL DEFAULT '03:00:00',
    ADD COLUMN ot_max_hours_per_day DECIMAL(6, 2) NOT NULL DEFAULT 2.00,
    ADD COLUMN minimum_monthly_wage DECIMAL(12, 2) NOT NULL DEFAULT 1000.00,
    ADD COLUMN default_housing_allowance DECIMAL(12, 2) NOT NULL DEFAULT 500.00,
    ADD COLUMN default_food_allowance DECIMAL(12, 2) NOT NULL DEFAULT 300.00;

-- Force-reset existing tenants to Qatar statutory defaults.
UPDATE companies
SET ot_day_rate_multiplier = 1.25,
    ot_night_friday_holiday_rate_multiplier = 1.50,
    ot_night_start_time = '21:00:00',
    ot_night_end_time = '03:00:00',
    ot_max_hours_per_day = 2.00,
    minimum_monthly_wage = 1000.00,
    default_housing_allowance = 500.00,
    default_food_allowance = 300.00;

ALTER TABLE employee_compensation
    ADD COLUMN food_allowance DOUBLE NOT NULL DEFAULT 0;
