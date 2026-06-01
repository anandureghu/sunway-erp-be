-- =============================================================================
-- V1: Full initial schema — created from Hibernate-generated tables.
-- All statements use CREATE TABLE IF NOT EXISTS so this migration is safe
-- to run against an existing database that already has these tables.
--
-- job_codes is intentionally created WITHOUT company_id here; V20260512
-- adds that column and fixes the constraints.
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `currency` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `country_name` varchar(255) DEFAULT NULL,
  `currency_code` varchar(3) NOT NULL,
  `currency_name` varchar(255) NOT NULL,
  `currency_symbol` varchar(5) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `companies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `city` varchar(50) DEFAULT NULL,
  `company_code` varchar(3) DEFAULT NULL,
  `company_name` varchar(50) NOT NULL,
  `computer_card` varchar(20) DEFAULT NULL,
  `country` varchar(50) DEFAULT NULL,
  `cr_no` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(50) DEFAULT NULL,
  `is_finance_enabled` bit(1) NOT NULL,
  `is_hr_enabled` bit(1) NOT NULL,
  `is_inventory_enabled` bit(1) NOT NULL,
  `is_tax_active` bit(1) NOT NULL,
  `no_of_employees` varchar(20) DEFAULT NULL,
  `phone_no` varchar(20) DEFAULT NULL,
  `state` varchar(50) DEFAULT NULL,
  `street` varchar(50) DEFAULT NULL,
  `tax_rate` varchar(3) DEFAULT NULL,
  `currency` bigint DEFAULT NULL,
  `default_bank_account_id` bigint DEFAULT NULL,
  `default_purchase_credit_account_id` bigint DEFAULT NULL,
  `default_purchase_debit_account_id` bigint DEFAULT NULL,
  `default_sales_credit_account_id` bigint DEFAULT NULL,
  `default_sales_debit_account_id` bigint DEFAULT NULL,
  `invoice_footer_billing_email` varchar(120) DEFAULT NULL,
  `invoice_footer_company_line` varchar(300) DEFAULT NULL,
  `invoice_footer_signature_note` varchar(300) DEFAULT NULL,
  `invoice_footer_support_email` varchar(120) DEFAULT NULL,
  `invoice_footer_tax_line` varchar(300) DEFAULT NULL,
  `invoice_header_subtitle` varchar(200) DEFAULT NULL,
  `invoice_notes_paid` varchar(1000) DEFAULT NULL,
  `invoice_notes_unpaid` varchar(1000) DEFAULT NULL,
  `invoice_public_base_url` varchar(500) DEFAULT NULL,
  `invoice_qr_enabled` bit(1) NOT NULL,
  `invoice_terms` varchar(4000) DEFAULT NULL,
  `billing_email` varchar(120) DEFAULT NULL,
  `company_email` varchar(120) DEFAULT NULL,
  `website_url` varchar(255) DEFAULT NULL,
  `payroll_employer_eid` varchar(64) DEFAULT NULL,
  `payroll_payer_bank_short_name` varchar(32) DEFAULT NULL,
  `payroll_payer_eid` varchar(64) DEFAULT NULL,
  `payroll_payer_iban` varchar(64) DEFAULT NULL,
  `payroll_payer_qid` varchar(64) DEFAULT NULL,
  `payroll_sif_version` varchar(16) DEFAULT NULL,
  `logo_url` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK14xol0839rme2tfq2vljssun9` (`currency`),
  CONSTRAINT `FK14xol0839rme2tfq2vljssun9` FOREIGN KEY (`currency`) REFERENCES `currency` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `company_roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `created_date` datetime(6) DEFAULT NULL,
  `description` text,
  `name` varchar(100) NOT NULL,
  `updated_date` datetime(6) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_role_name` (`company_id`,`name`),
  CONSTRAINT `FKipbhyb8fc9q18trxoou7ncf2g` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_role` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(255) NOT NULL,
  `force_password_reset` bit(1) NOT NULL,
  `full_name` varchar(255) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('ACCOUNTANT','ADMIN','AP_AR_CLERK','AUDITOR_EXTERNAL','CONTROLLER','FINANCE_MANAGER','HR','SUPER_ADMIN','USER') NOT NULL,
  `username` varchar(255) NOT NULL,
  `company_id` bigint DEFAULT NULL,
  `company_role_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_email` (`email`),
  UNIQUE KEY `uk_users_username` (`username`),
  KEY `FKin8gn4o1hpiwe6qe4ey7ykwq7` (`company_id`),
  KEY `FK3wlr6f8gt61xhijqga698mfrb` (`company_role_id`),
  CONSTRAINT `FK3wlr6f8gt61xhijqga698mfrb` FOREIGN KEY (`company_role_id`) REFERENCES `company_roles` (`id`),
  CONSTRAINT `FKin8gn4o1hpiwe6qe4ey7ykwq7` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `departments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `department_code` varchar(255) NOT NULL,
  `department_name` varchar(255) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `manager_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK89g8qie2y696a3tarmty43sq9` (`department_code`),
  KEY `FKoq64wrpwbvd4lq19c3qyxykl0` (`company_id`),
  KEY `FK56q3esufky8u69xbmo4n63c4r` (`manager_id`),
  CONSTRAINT `FKoq64wrpwbvd4lq19c3qyxykl0` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employees` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `birthplace` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `date_of_birth` date DEFAULT NULL,
  `employee_no` varchar(255) NOT NULL,
  `first_name` varchar(50) NOT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `hometown` varchar(100) DEFAULT NULL,
  `identification` varchar(100) DEFAULT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `join_date` date DEFAULT NULL,
  `last_name` varchar(50) NOT NULL,
  `marital_status` varchar(30) DEFAULT NULL,
  `nationality` varchar(100) DEFAULT NULL,
  `notes` text,
  `prefix` varchar(20) DEFAULT NULL,
  `religion` varchar(100) DEFAULT NULL,
  `status` enum('ACTIVE','INACTIVE','ON_LEAVE') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `department_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `role` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK62tjkkmdtpl2vmweafp9rensh` (`employee_no`),
  UNIQUE KEY `UKj2dmgsma6pont6kf7nic9elpd` (`user_id`),
  KEY `FK1ekpcbo0lmdx6ou8e3fh9j4lq` (`company_id`),
  KEY `FKgy4qe3dnqrm3ktd76sxp7n4c2` (`department_id`),
  CONSTRAINT `FK1ekpcbo0lmdx6ou8e3fh9j4lq` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FK69x3vjuy1t5p18a5llb8h2fjx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKgy4qe3dnqrm3ktd76sxp7n4c2` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Add deferred FK from departments.manager_id → employees (circular dep resolved here).
-- Wrapped in a procedure because IF NOT EXISTS is not supported for ADD CONSTRAINT.
DROP PROCEDURE IF EXISTS add_dept_manager_fk;
DELIMITER //
CREATE PROCEDURE add_dept_manager_fk()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
         WHERE TABLE_SCHEMA   = DATABASE()
           AND TABLE_NAME     = 'departments'
           AND CONSTRAINT_NAME = 'FK56q3esufky8u69xbmo4n63c4r'
    ) THEN
        ALTER TABLE `departments`
            ADD CONSTRAINT `FK56q3esufky8u69xbmo4n63c4r`
            FOREIGN KEY (`manager_id`) REFERENCES `employees` (`id`);
    END IF;
END //
DELIMITER ;
CALL add_dept_manager_fk();
DROP PROCEDURE add_dept_manager_fk;

CREATE TABLE IF NOT EXISTS `division` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(3) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `company_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  `manager_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbtcfi56r9kx631us0sfitvxli` (`company_id`,`code`),
  KEY `FKfa72lcput7v66if00l4j2p0u5` (`department_id`),
  KEY `FKoshvx9nfqmsuh0ng6sl5ggrc7` (`manager_id`),
  CONSTRAINT `FKa3halryary503uqcqmqy2gdpc` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKfa72lcput7v66if00l4j2p0u5` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`),
  CONSTRAINT `FKoshvx9nfqmsuh0ng6sl5ggrc7` FOREIGN KEY (`manager_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `accounting_periods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` date NOT NULL,
  `end_date` date NOT NULL,
  `period_name` varchar(20) NOT NULL,
  `start_date` date NOT NULL,
  `status` enum('CLOSED','OPEN') NOT NULL,
  `company_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKtrl7wdmcn27622kpy138muysp` (`company_id`),
  CONSTRAINT `FKtrl7wdmcn27622kpy138muysp` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `allowance_types` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4obsqv3tgi386crsw6hubbedx` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `appraisal_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_date` datetime(6) DEFAULT NULL,
  `cycle_name` varchar(255) DEFAULT NULL,
  `enable_mid_year` bit(1) DEFAULT NULL,
  `enablepip` bit(1) DEFAULT NULL,
  `enable_self_assessment` bit(1) DEFAULT NULL,
  `end_month` varchar(255) NOT NULL,
  `max_goals` int DEFAULT NULL,
  `min_goals` int DEFAULT NULL,
  `start_month` varchar(255) NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `updated_date` datetime(6) DEFAULT NULL,
  `year` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `appraisal_role_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(255) DEFAULT NULL,
  `config_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK4nv9adg9cl5gn6odf5hwy50lw` (`config_id`),
  CONSTRAINT `FK4nv9adg9cl5gn6odf5hwy50lw` FOREIGN KEY (`config_id`) REFERENCES `appraisal_config` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `appraisal_goal_template` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `kpi` varchar(255) DEFAULT NULL,
  `weight` int DEFAULT NULL,
  `role_config_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKga0rin9vj3bf5lo6sn8ki4xyj` (`role_config_id`),
  CONSTRAINT `FKga0rin9vj3bf5lo6sn8ki4xyj` FOREIGN KEY (`role_config_id`) REFERENCES `appraisal_role_config` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `bank_accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_holder_name` varchar(100) NOT NULL,
  `account_number` varchar(30) NOT NULL,
  `bank_name` varchar(100) NOT NULL,
  `branch_name` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `ifsc_code` varchar(20) DEFAULT NULL,
  `primary_account` bit(1) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7cen2o0hy6qm7nrqc81c9h046` (`company_id`,`account_number`),
  CONSTRAINT `FKdvnlbhowmkddn62i80sbmmtny` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `chart_of_accounts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_code` varchar(20) NOT NULL,
  `account_name` varchar(255) NOT NULL,
  `account_no` varchar(7) NOT NULL,
  `as_of_date` datetime(6) DEFAULT NULL,
  `balance` decimal(38,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `initial_balance_set` bit(1) NOT NULL,
  `inter_company_number` varchar(3) NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `project_code` varchar(6) DEFAULT NULL,
  `type` tinyint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `year` int NOT NULL,
  `company_id` bigint NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2yshq4betcn6mtdyml98p3c2w` (`company_id`,`account_code`),
  KEY `FK228ryrb85a1u1soivuqif8dga` (`created_by`),
  KEY `FKftxgawspl94a8ggsq3yg1072h` (`department_id`),
  KEY `FKt1046gd7mgo0v7rdnh6aa3per` (`parent_id`),
  KEY `FKns1hplb2qb4ng1deh526v1ilo` (`updated_by`),
  CONSTRAINT `FK228ryrb85a1u1soivuqif8dga` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FK6accfrufhwecxlddejwudm6u3` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKftxgawspl94a8ggsq3yg1072h` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`),
  CONSTRAINT `FKns1hplb2qb4ng1deh526v1ilo` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKt1046gd7mgo0v7rdnh6aa3per` FOREIGN KEY (`parent_id`) REFERENCES `chart_of_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `budget_headers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(38,2) DEFAULT NULL,
  `budget_name` varchar(150) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `fiscal_year` varchar(255) NOT NULL,
  `is_active` bit(1) DEFAULT NULL,
  `revise_count` bigint DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `status` tinyint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `approved_by_user_id` bigint DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `parent_budget` bigint DEFAULT NULL,
  `updated_by_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7la29w5lurthqbkc084nrriab` (`approved_by_user_id`),
  KEY `FK9by85ysl3fqoob30qs0gog8k2` (`company_id`),
  KEY `FKeuoode7e54d1497xosie9n6mm` (`created_by_user_id`),
  KEY `FKdl2ew6lncwtnqjnyu8c1qjbl7` (`parent_budget`),
  KEY `FKku35vud7pid0n7qrmsyksyb0g` (`updated_by_user_id`),
  CONSTRAINT `FK7la29w5lurthqbkc084nrriab` FOREIGN KEY (`approved_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK9by85ysl3fqoob30qs0gog8k2` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKdl2ew6lncwtnqjnyu8c1qjbl7` FOREIGN KEY (`parent_budget`) REFERENCES `budget_headers` (`id`),
  CONSTRAINT `FKeuoode7e54d1497xosie9n6mm` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKku35vud7pid0n7qrmsyksyb0g` FOREIGN KEY (`updated_by_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `budget_lines` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(38,2) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `end_date` date NOT NULL,
  `notes` varchar(250) DEFAULT NULL,
  `project_id` varchar(255) DEFAULT NULL,
  `start_date` date NOT NULL,
  `status` tinyint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `account_id` bigint NOT NULL,
  `approved_by_user_id` bigint DEFAULT NULL,
  `budget_header_id` bigint NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `updated_by_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKepl6fe246425xqd22qd0hsrp1` (`account_id`),
  KEY `FKcmgyoc09m14wh66qiwv1a0aon` (`approved_by_user_id`),
  KEY `FKrkvtlax85dhts2tkyjds0r8i0` (`budget_header_id`),
  KEY `FKs3sj108dhrgk5vv68honw9v50` (`created_by_user_id`),
  KEY `FK3o49rkbqdhn6svrtuu5au5vn` (`department_id`),
  KEY `FKmnf6uokbpcw9yywirjl6lumtt` (`updated_by_user_id`),
  CONSTRAINT `FK3o49rkbqdhn6svrtuu5au5vn` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`),
  CONSTRAINT `FKcmgyoc09m14wh66qiwv1a0aon` FOREIGN KEY (`approved_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKepl6fe246425xqd22qd0hsrp1` FOREIGN KEY (`account_id`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FKmnf6uokbpcw9yywirjl6lumtt` FOREIGN KEY (`updated_by_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKrkvtlax85dhts2tkyjds0r8i0` FOREIGN KEY (`budget_header_id`) REFERENCES `budget_headers` (`id`),
  CONSTRAINT `FKs3sj108dhrgk5vv68honw9v50` FOREIGN KEY (`created_by_user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhjumwkvbhuo13s0cxvhd57l93` (`company_id`,`parent_id`,`code`),
  KEY `FK5yfru0au6kpyqs4tonky5vfne` (`created_by`),
  KEY `FKsaok720gsu4u2wrgbk10b5n8d` (`parent_id`),
  KEY `FKnbfq7vefwik42v5ka12ekr4hv` (`updated_by`),
  CONSTRAINT `FK5yfru0au6kpyqs4tonky5vfne` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKdp7yf5up5coo4njjvq1dwdxif` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKnbfq7vefwik42v5ka12ekr4hv` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKsaok720gsu4u2wrgbk10b5n8d` FOREIGN KEY (`parent_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `code_sequences` (
  `code_key` varchar(255) NOT NULL,
  `last_number` bigint NOT NULL,
  PRIMARY KEY (`code_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `company_invoice_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `invoice_footer_billing_email` varchar(120) DEFAULT NULL,
  `invoice_footer_company_line` varchar(300) DEFAULT NULL,
  `invoice_footer_signature_note` varchar(300) DEFAULT NULL,
  `invoice_footer_support_email` varchar(120) DEFAULT NULL,
  `invoice_footer_tax_line` varchar(300) DEFAULT NULL,
  `invoice_header_subtitle` varchar(200) DEFAULT NULL,
  `invoice_notes_paid` varchar(1000) DEFAULT NULL,
  `invoice_notes_unpaid` varchar(1000) DEFAULT NULL,
  `invoice_qr_enabled` bit(1) NOT NULL,
  `invoice_terms` varchar(4000) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKj6p088odrk3tmxd63la5vfx4l` (`company_id`),
  CONSTRAINT `FKbhfp2evlau5kr0qsakyy05c25` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `company_leave_policies` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `allowed_gender` varchar(255) DEFAULT NULL,
  `default_days` int NOT NULL,
  `gender_restricted` bit(1) NOT NULL,
  `leave_type` varchar(255) NOT NULL,
  `paid` bit(1) NOT NULL,
  `role` varchar(255) NOT NULL,
  `company_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKi0t4jyee2xigvt32wv32e2b5s` (`company_id`,`role`,`leave_type`),
  CONSTRAINT `FKt1fubp96m5w6qqeahu2uj4tcg` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `company_properties` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `date_given` date NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `item_code` varchar(50) NOT NULL,
  `item_name` varchar(100) NOT NULL,
  `item_status` enum('ASSIGNED','DAMAGED','LOST','RETURNED') NOT NULL,
  `return_date` date DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKiqrj8q1tkyaixwhaag6go81s5` (`employee_id`),
  CONSTRAINT `FKiqrj8q1tkyaixwhaag6go81s5` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `company_role_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approve` bit(1) NOT NULL,
  `create_permission` bit(1) NOT NULL,
  `delete_permission` bit(1) NOT NULL,
  `edit_permission` bit(1) NOT NULL,
  `module` enum('APPRAISAL','CURRENT_JOB','DEPENDENTS','EMPLOYEE_PROFILE','HR_REPORTS','HR_SETTINGS','IMMIGRATION','LEAVES','LOANS','SALARY') NOT NULL,
  `view_all` bit(1) NOT NULL,
  `view_own` bit(1) NOT NULL,
  `company_role_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_company_role_module` (`company_role_id`,`module`),
  CONSTRAINT `FK7v3jdh3wiytotnhspcaxlf3ci` FOREIGN KEY (`company_role_id`) REFERENCES `company_roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `contract_sequence` (
  `next_val` bigint DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `contracts` (
  `id` bigint NOT NULL,
  `attachment_path` varchar(255) DEFAULT NULL,
  `contract_code` varchar(255) NOT NULL,
  `contract_period_months` int DEFAULT NULL,
  `contract_type` enum('CONSULTANT','INTERN','PERMANENT','TEMPORARY') NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `deleted` bit(1) NOT NULL,
  `effective_date` date NOT NULL,
  `expiration_date` date DEFAULT NULL,
  `notice_period_days` int DEFAULT NULL,
  `salary_rate_type` varchar(255) DEFAULT NULL,
  `signature_date` date DEFAULT NULL,
  `signed_by` varchar(255) DEFAULT NULL,
  `status` enum('ACTIVE','DRAFT','EXPIRED','TERMINATED') NOT NULL,
  `terms_and_conditions` varchar(2000) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_code` (`contract_code`),
  KEY `FKf5c9xgkxh0n28hbhsgo5rkq58` (`employee_id`),
  CONSTRAINT `FKf5c9xgkxh0n28hbhsgo5rkq58` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `invoices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(38,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `due_date` date DEFAULT NULL,
  `grace_period` int DEFAULT NULL,
  `interest_rate` decimal(38,2) DEFAULT NULL,
  `invoice_date` date DEFAULT NULL,
  `invoice_id` varchar(255) DEFAULT NULL,
  `item_description` varchar(500) DEFAULT NULL,
  `notes_remarks` text,
  `open_amount` decimal(38,2) DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `outstanding` decimal(38,2) DEFAULT NULL,
  `paid_date` date DEFAULT NULL,
  `party_classification` varchar(255) DEFAULT NULL,
  `pdf_url` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `to_party` varchar(255) DEFAULT NULL,
  `type` tinyint DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `credit_account` bigint NOT NULL,
  `debit_account` bigint NOT NULL,
  `bank_account_id` bigint DEFAULT NULL,
  `discount_amount` decimal(38,2) DEFAULT NULL,
  `subtotal_amount` decimal(38,2) DEFAULT NULL,
  `tax_amount` decimal(38,2) DEFAULT NULL,
  `document_source` enum('EXTERNAL_LINK','GENERATED','SUPPLIER_UPLOAD') DEFAULT NULL,
  `external_document_url` varchar(2000) DEFAULT NULL,
  `supplier_invoice_number` varchar(120) DEFAULT NULL,
  `archived` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKin8ejtpjwbmf9llvtr68vty6t` (`invoice_id`),
  KEY `FK9uwtrg1887fbqa4gb98n6hik6` (`company_id`),
  KEY `FKcc8llatqpgh1f5ogukxq2dys` (`credit_account`),
  KEY `FKqu1eue0qr154jhmnfjf7no3jk` (`debit_account`),
  KEY `FKmuxcu2tg2nvnh1he3btmsv9ru` (`bank_account_id`),
  CONSTRAINT `FK9uwtrg1887fbqa4gb98n6hik6` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKcc8llatqpgh1f5ogukxq2dys` FOREIGN KEY (`credit_account`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FKmuxcu2tg2nvnh1he3btmsv9ru` FOREIGN KEY (`bank_account_id`) REFERENCES `bank_accounts` (`id`),
  CONSTRAINT `FKqu1eue0qr154jhmnfjf7no3jk` FOREIGN KEY (`debit_account`) REFERENCES `chart_of_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `credit_notes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(38,2) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `credit_date` date DEFAULT NULL,
  `credit_note_number` varchar(255) NOT NULL,
  `project` varchar(255) DEFAULT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `remaining_amount` decimal(38,2) NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `invoice_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKj2kxpwagu0246e27aen971e3k` (`credit_note_number`,`company_id`),
  UNIQUE KEY `UKabchkricq0sc3vdu6x1eseabr` (`credit_note_number`),
  KEY `FK4kn505m7rd4qjomvm1ocgk37a` (`company_id`),
  KEY `FK17v6ewtnp6crd16dih7jdopev` (`invoice_id`),
  CONSTRAINT `FK17v6ewtnp6crd16dih7jdopev` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`),
  CONSTRAINT `FK4kn505m7rd4qjomvm1ocgk37a` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `customers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `city` varchar(100) DEFAULT NULL,
  `contact_person_name` varchar(120) DEFAULT NULL,
  `country` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `credit_limit` decimal(38,2) DEFAULT NULL,
  `currency_code` varchar(3) NOT NULL,
  `customer_name` varchar(150) NOT NULL,
  `customer_type` varchar(50) DEFAULT NULL,
  `email` varchar(120) DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `payment_terms` varchar(50) NOT NULL,
  `phone_no` varchar(30) DEFAULT NULL,
  `state` varchar(100) DEFAULT NULL,
  `street` varchar(150) DEFAULT NULL,
  `tax_id` varchar(50) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `website_url` varchar(200) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKm6xjfk3oq0lwbxin2upeog0ji` (`tax_id`),
  KEY `FKl9seru5curo311xhtpbxe3074` (`company_id`),
  CONSTRAINT `FKl9seru5curo311xhtpbxe3074` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `document_sequence` (
  `document_type` varchar(255) NOT NULL,
  `next_value` bigint DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  PRIMARY KEY (`document_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_addresses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address_type` varchar(255) DEFAULT NULL,
  `city` varchar(255) DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `address_line1` varchar(255) NOT NULL,
  `address_line2` varchar(255) DEFAULT NULL,
  `postal_code` varchar(255) DEFAULT NULL,
  `is_primary` bit(1) DEFAULT NULL,
  `state` varchar(255) DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK4kelvir0i8vgoydc61kl4jpgv` (`employee_id`),
  CONSTRAINT `FK4kelvir0i8vgoydc61kl4jpgv` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_appraisals` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_date` datetime(6) DEFAULT NULL,
  `employee_acknowledge` varchar(255) DEFAULT NULL,
  `employee_comments` text,
  `employee_rebuttal` text,
  `manager_comments` text,
  `month` varchar(20) NOT NULL,
  `overall_score` double DEFAULT NULL,
  `status` varchar(30) DEFAULT NULL,
  `updated_date` datetime(6) DEFAULT NULL,
  `year` int NOT NULL,
  `config_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_appraisal_month_year` (`employee_id`,`year`,`month`),
  KEY `idx_employee_appraisal_employee` (`employee_id`),
  KEY `idx_employee_appraisal_year` (`year`),
  KEY `idx_employee_appraisal_month` (`month`),
  KEY `FKm6yigvaykcnebagp56r99sb3h` (`config_id`),
  CONSTRAINT `FK8dnyotbiei23s06c81objfy8t` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`),
  CONSTRAINT `FKm6yigvaykcnebagp56r99sb3h` FOREIGN KEY (`config_id`) REFERENCES `appraisal_config` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_appraisal_goals` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` text,
  `kpi` varchar(255) DEFAULT NULL,
  `manager_comment` text,
  `manager_rating` int DEFAULT NULL,
  `self_comment` text,
  `self_rating` int DEFAULT NULL,
  `weight` int DEFAULT NULL,
  `appraisal_id` bigint NOT NULL,
  `template_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_appraisal_goal_appraisal` (`appraisal_id`),
  KEY `FKgu6ev24sn8lvkfsclx50s7sa6` (`template_id`),
  CONSTRAINT `FK7lhb252twsbxhl11t16wrp9hn` FOREIGN KEY (`appraisal_id`) REFERENCES `employee_appraisals` (`id`),
  CONSTRAINT `FKgu6ev24sn8lvkfsclx50s7sa6` FOREIGN KEY (`template_id`) REFERENCES `appraisal_goal_template` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_bank_details` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_no` varchar(255) NOT NULL,
  `account_type` varchar(255) NOT NULL,
  `bank_branch` varchar(255) NOT NULL,
  `bank_name` varchar(255) NOT NULL,
  `city` varchar(255) DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `iban` varchar(64) DEFAULT NULL,
  `remarks` varchar(500) DEFAULT NULL,
  `state` varchar(255) DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  `bank_short_name` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlubeqplhmxo40vv4ao87yptj2` (`employee_id`),
  CONSTRAINT `FKlubeqplhmxo40vv4ao87yptj2` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_compensation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `basic_salary` double NOT NULL,
  `effective_from` date NOT NULL,
  `effective_to` date DEFAULT NULL,
  `housing_allowance` double NOT NULL,
  `housing_type` enum('ALLOWANCE','COMPANY_PROVIDED','NONE') NOT NULL,
  `other_allowance` double NOT NULL,
  `status` varchar(20) NOT NULL,
  `total_compensation` double NOT NULL,
  `transportation_allowance` double NOT NULL,
  `transportation_type` enum('ALLOWANCE','COMPANY_PROVIDED','NONE') NOT NULL,
  `travel_allowance` double NOT NULL,
  `travel_type` enum('ALLOWANCE','COMPANY_PROVIDED','NONE') NOT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKt9lbqtht1x90xi8jn0jush9ew` (`employee_id`),
  CONSTRAINT `FKt9lbqtht1x90xi8jn0jush9ew` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_contact_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alt_phone` varchar(255) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `notes` varchar(1000) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkr6dqhpmpc8qjx8sult8jluup` (`employee_id`),
  CONSTRAINT `FKr57fmt6jpmqb9ayx2osgw6wt3` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_current_job` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `effective_from` date DEFAULT NULL,
  `expected_end_date` date DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `work_city` varchar(255) DEFAULT NULL,
  `work_country` varchar(255) DEFAULT NULL,
  `work_location` varchar(255) DEFAULT NULL,
  `department_id` bigint NOT NULL,
  `employee_id` bigint NOT NULL,
  `job_code_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1ytwtudo9t7f5rsb4mdd4lf9c` (`employee_id`),
  KEY `FKa3dha2dxtsc2v6e099gld84rq` (`department_id`),
  KEY `FKlkmdrpnap1q8pe8ahme7k4cly` (`job_code_id`),
  CONSTRAINT `FKa3dha2dxtsc2v6e099gld84rq` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`),
  CONSTRAINT `FKapkk10kd4yvgi1tl4xogw1gt0` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`),
  CONSTRAINT `FKlkmdrpnap1q8pe8ahme7k4cly` FOREIGN KEY (`job_code_id`) REFERENCES `job_codes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_dependents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address_line1` varchar(150) DEFAULT NULL,
  `address_line2` varchar(150) DEFAULT NULL,
  `city` varchar(100) DEFAULT NULL,
  `country` varchar(100) DEFAULT NULL,
  `date_of_birth` date DEFAULT NULL,
  `first_name` varchar(50) NOT NULL,
  `gender` varchar(20) DEFAULT NULL,
  `last_name` varchar(50) NOT NULL,
  `marital_status` varchar(50) DEFAULT NULL,
  `middle_name` varchar(50) DEFAULT NULL,
  `national_id` varchar(50) DEFAULT NULL,
  `nationality` varchar(50) DEFAULT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  `postal_code` varchar(20) DEFAULT NULL,
  `relationship` varchar(50) DEFAULT NULL,
  `state` varchar(100) DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKa7nxf79k465m2xag28k9wtrv5` (`employee_id`),
  CONSTRAINT `FKa7nxf79k465m2xag28k9wtrv5` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_education` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `awards` text,
  `degree_earned` varchar(255) DEFAULT NULL,
  `major` varchar(255) DEFAULT NULL,
  `notes` text,
  `school_address` varchar(255) DEFAULT NULL,
  `school_name` varchar(255) DEFAULT NULL,
  `year_graduated` int DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKp4wlgwluyoknphmwhgn2o5pks` (`employee_id`),
  CONSTRAINT `FKp4wlgwluyoknphmwhgn2o5pks` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_experience` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_address` text,
  `company_name` varchar(255) DEFAULT NULL,
  `job_title` varchar(255) DEFAULT NULL,
  `last_date_worked` date DEFAULT NULL,
  `notes` text,
  `number_of_years` int DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcv1hvbh746mvsnycur9njvrgb` (`employee_id`),
  CONSTRAINT `FKcv1hvbh746mvsnycur9njvrgb` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_leave_balances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `leave_type` varchar(255) NOT NULL,
  `remaining_leaves` int NOT NULL,
  `total_leaves` int NOT NULL,
  `employee_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKl98ne46i9jp96cfwfb8bi9jfv` (`employee_id`,`leave_type`),
  CONSTRAINT `FKna2wpd1rpr71q3tljsmsjm1jn` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_leaves` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `date_reported` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `leave_code` varchar(255) DEFAULT NULL,
  `leave_status` varchar(255) DEFAULT NULL,
  `leave_type` varchar(255) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `total_days` int DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  `include_weekends` bit(1) DEFAULT NULL,
  `supporting_document_path` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK49gewuj53lf7foeyfpvcharf6` (`employee_id`),
  CONSTRAINT `FK49gewuj53lf7foeyfpvcharf6` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_loans` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `balance` double NOT NULL,
  `end_date` date NOT NULL,
  `loan_amount` double NOT NULL,
  `loan_code` varchar(255) NOT NULL,
  `loan_period` int NOT NULL,
  `loan_type` enum('CAR_LOAN','EDUCATION_LOAN','HOUSING_LOAN','MEDICAL_LOAN','PERSONAL_LOAN') NOT NULL,
  `monthly_deduction` double NOT NULL,
  `notes` varchar(1000) DEFAULT NULL,
  `start_date` date NOT NULL,
  `status` varchar(255) NOT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8u2pdxkbh26sec4ux2yb4f6c1` (`loan_code`),
  KEY `FK3u7s992j83pc4h8fdjlithsm7` (`employee_id`),
  CONSTRAINT `FK3u7s992j83pc4h8fdjlithsm7` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_no_seq` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_passports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `expiry_date` date DEFAULT NULL,
  `issue_country` varchar(255) NOT NULL,
  `issue_date` date DEFAULT NULL,
  `name_as_passport` varchar(255) NOT NULL,
  `nationality` varchar(255) NOT NULL,
  `passport_no` varchar(255) NOT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2oydfquq1ce4kpa5ei7g04a67` (`employee_id`),
  CONSTRAINT `FKdeupiou7hsh2kudea5fbuma1u` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approve` bit(1) NOT NULL,
  `create_permission` bit(1) NOT NULL,
  `delete_permission` bit(1) NOT NULL,
  `edit_permission` bit(1) NOT NULL,
  `module` enum('APPRAISAL','CURRENT_JOB','DEPENDENTS','EMPLOYEE_PROFILE','HR_REPORTS','HR_SETTINGS','IMMIGRATION','LEAVES','LOANS','SALARY') NOT NULL,
  `view_all` bit(1) NOT NULL,
  `view_own` bit(1) NOT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_permission_module` (`employee_id`,`module`),
  CONSTRAINT `FKmoghffk75jlh1ky6keycegxrm` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_residence_permits` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `duration_type` varchar(255) NOT NULL,
  `end_date` date DEFAULT NULL,
  `issue_authority` varchar(255) DEFAULT NULL,
  `issue_place` varchar(255) DEFAULT NULL,
  `nationality` varchar(255) NOT NULL,
  `occupation` varchar(255) NOT NULL,
  `permit_id_number` varchar(50) NOT NULL,
  `start_date` date DEFAULT NULL,
  `visa_duration` varchar(255) NOT NULL,
  `visa_status` varchar(255) DEFAULT NULL,
  `visa_type` varchar(255) NOT NULL,
  `employee_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKidyapwmcqe04jebrofb0bn4rh` (`employee_id`),
  UNIQUE KEY `UKox98fw7lo9wx7fnot825d5f2s` (`permit_id_number`),
  CONSTRAINT `FKoioauvqvwec72l8i3fo2g7m4t` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `employee_timesheets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attendance_date` date NOT NULL,
  `check_in_time` datetime(6) DEFAULT NULL,
  `check_out_time` datetime(6) DEFAULT NULL,
  `employee_id` bigint NOT NULL,
  `status` enum('CHECKED_IN','CHECKED_OUT','NOT_CHECKED_IN') NOT NULL,
  `worked_minutes` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `enum_role_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approve` bit(1) NOT NULL,
  `create_permission` bit(1) NOT NULL,
  `delete_permission` bit(1) NOT NULL,
  `edit_permission` bit(1) NOT NULL,
  `module` enum('APPRAISAL','CURRENT_JOB','DEPENDENTS','EMPLOYEE_PROFILE','HR_REPORTS','HR_SETTINGS','IMMIGRATION','LEAVES','LOANS','SALARY') NOT NULL,
  `role` enum('ACCOUNTANT','ADMIN','AP_AR_CLERK','AUDITOR_EXTERNAL','CONTROLLER','FINANCE_MANAGER','HR','SUPER_ADMIN','USER') NOT NULL,
  `view_all` bit(1) NOT NULL,
  `view_own` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_enum_role_module` (`role`,`module`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `gl_account_balances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_id` bigint NOT NULL,
  `accounting_period_end` datetime(6) DEFAULT NULL,
  `accounting_period_start` datetime(6) DEFAULT NULL,
  `as_of_date` datetime(6) DEFAULT NULL,
  `balance` decimal(38,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `fiscal_year` varchar(255) DEFAULT NULL,
  `total_assets` decimal(38,2) DEFAULT NULL,
  `total_expenses` decimal(38,2) DEFAULT NULL,
  `total_liabilities` decimal(38,2) DEFAULT NULL,
  `total_revenue` decimal(38,2) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `vendor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approved` bit(1) NOT NULL,
  `city` varchar(100) DEFAULT NULL,
  `contact_person_name` varchar(120) DEFAULT NULL,
  `country` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `credit_limit` decimal(38,2) DEFAULT NULL,
  `currency_code` varchar(3) NOT NULL,
  `email` varchar(120) DEFAULT NULL,
  `fax` varchar(50) DEFAULT NULL,
  `is1099vendor` bit(1) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `payment_terms` varchar(50) NOT NULL,
  `phone_no` varchar(30) DEFAULT NULL,
  `rejected` bit(1) NOT NULL,
  `remarks` varchar(255) DEFAULT NULL,
  `street` varchar(150) DEFAULT NULL,
  `tax_id` varchar(50) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `vendor_name` varchar(150) NOT NULL,
  `website_url` varchar(200) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsv96wdql72b6t4enmxmp3j6fg` (`company_id`,`vendor_name`),
  UNIQUE KEY `UK5l6l6go3qwd9yv78f8s7ls92p` (`tax_id`),
  CONSTRAINT `FKgk5srf6ekjjt86qho0i0quybs` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `warehouses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `city` varchar(255) DEFAULT NULL,
  `code` varchar(255) NOT NULL,
  `contact_person_name` varchar(255) DEFAULT NULL,
  `country` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `pin` varchar(255) DEFAULT NULL,
  `status` varchar(255) DEFAULT NULL,
  `street` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `manager` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6ycscv7ubtal0aevf2aapf4m9` (`company_id`),
  KEY `FK21mnq798od8r3ua4p16t539qi` (`created_by`),
  KEY `FKistbkfw24f2kqwbffhebac1g8` (`manager`),
  KEY `FKpkm6ciomp6ybaqf05i8r93bff` (`updated_by`),
  CONSTRAINT `FK21mnq798od8r3ua4p16t539qi` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FK6ycscv7ubtal0aevf2aapf4m9` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKistbkfw24f2kqwbffhebac1g8` FOREIGN KEY (`manager`) REFERENCES `users` (`id`),
  CONSTRAINT `FKpkm6ciomp6ybaqf05i8r93bff` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `available` int DEFAULT NULL,
  `barcode` varchar(255) DEFAULT NULL,
  `brand` varchar(255) DEFAULT NULL,
  `category` varchar(255) DEFAULT NULL,
  `cost_price` decimal(18,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` text,
  `image_url` varchar(500) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  `maximum` int DEFAULT NULL,
  `minimum` int DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `quantity` int DEFAULT NULL,
  `reorder_level` int DEFAULT NULL,
  `reserved` int DEFAULT NULL,
  `selling_price` decimal(18,2) DEFAULT NULL,
  `serial_no` varchar(255) DEFAULT NULL,
  `sku` varchar(100) NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `sub_category` varchar(255) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `unit_measure` varchar(255) DEFAULT NULL,
  `unit_sale` decimal(18,2) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `warehouse_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6ekhs0v78950udvne2fj7y2ee` (`sku`),
  KEY `FK2rxt76eyddslg9sxiy4thtntm` (`company_id`),
  KEY `FKstbwb3fvemcf9j1yf64fey29i` (`created_by`),
  KEY `FKoo6ebdr4sxlll2v1ud97ddbbr` (`updated_by`),
  KEY `FKesjtuiq3dsdin8j637ofumv6v` (`warehouse_id`),
  CONSTRAINT `FK2rxt76eyddslg9sxiy4thtntm` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKesjtuiq3dsdin8j637ofumv6v` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`),
  CONSTRAINT `FKoo6ebdr4sxlll2v1ud97ddbbr` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKstbwb3fvemcf9j1yf64fey29i` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `item_warehouse_stock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantity_on_hand` int NOT NULL,
  `reserved` int NOT NULL,
  `item_id` bigint NOT NULL,
  `warehouse_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKatjdbrpalnwc85q9kueu0fa7f` (`item_id`,`warehouse_id`),
  KEY `FK7yrcyl3jgyjacnw1xbiq4xlnl` (`warehouse_id`),
  CONSTRAINT `FK7yrcyl3jgyjacnw1xbiq4xlnl` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`),
  CONSTRAINT `FKr2xaghhkyypt18j8mo4c24qw7` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- job_codes: intentionally WITHOUT company_id — V20260512 adds it and fixes constraints.
CREATE TABLE IF NOT EXISTS `job_codes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `code` varchar(255) NOT NULL,
  `grade` varchar(255) NOT NULL,
  `level` varchar(255) NOT NULL,
  `title` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqieucx7kewuth4gfx29w76u40` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `journal_entries` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(18,2) NOT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `je_number` varchar(255) DEFAULT NULL,
  `source` varchar(255) DEFAULT NULL,
  `status` enum('APPROVED','ON_HOLD','PENDING_APPROVAL','REJECTED') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `created_by` bigint NOT NULL,
  `credit_account_id` bigint NOT NULL,
  `debit_account_id` bigint NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKnbx3hpimcasxsww2w0amhcpqj` (`je_number`),
  KEY `FKkyctmhee9nb5xsgok0e1ryspa` (`approved_by`),
  KEY `FK5qeq7p3651jjc5sc11juxnve4` (`company_id`),
  KEY `FKl534qxwkwnitlrk9rw4hxywtw` (`created_by`),
  KEY `FK8wlsqd7r6i95ybiuo5x7indhm` (`credit_account_id`),
  KEY `FKpd3d0suw1ro4w6lf3vxikv54g` (`debit_account_id`),
  KEY `FKiji3alo7h9jk7ajvqvsn4i8s4` (`updated_by`),
  CONSTRAINT `FK5qeq7p3651jjc5sc11juxnve4` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FK8wlsqd7r6i95ybiuo5x7indhm` FOREIGN KEY (`credit_account_id`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FKiji3alo7h9jk7ajvqvsn4i8s4` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKkyctmhee9nb5xsgok0e1ryspa` FOREIGN KEY (`approved_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKl534qxwkwnitlrk9rw4hxywtw` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKpd3d0suw1ro4w6lf3vxikv54g` FOREIGN KEY (`debit_account_id`) REFERENCES `chart_of_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `loan_sequence` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `current_sequence` bigint NOT NULL,
  `loan_type` enum('CAR_LOAN','EDUCATION_LOAN','HOUSING_LOAN','MEDICAL_LOAN','PERSONAL_LOAN') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKefnl151toquuvs1eaoh0lhdfk` (`loan_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `agreement` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(255) DEFAULT NULL,
  `estimated_delivery_date` datetime(6) DEFAULT NULL,
  `notes_remarks` varchar(255) DEFAULT NULL,
  `order_date` datetime(6) DEFAULT NULL,
  `order_id` bigint DEFAULT NULL,
  `order_name` varchar(255) DEFAULT NULL,
  `order_status` varchar(255) DEFAULT NULL,
  `shipment_date` datetime(6) DEFAULT NULL,
  `supplier` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhmsk25beh6atojvle1xuymjj0` (`order_id`),
  KEY `FK5owe79isosbw3lfwwn4ofbeyg` (`supplier`),
  CONSTRAINT `FK5owe79isosbw3lfwwn4ofbeyg` FOREIGN KEY (`supplier`) REFERENCES `vendor` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(18,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `effective_date` date DEFAULT NULL,
  `invoice_id` varchar(64) DEFAULT NULL,
  `notes` varchar(500) DEFAULT NULL,
  `payment_code` varchar(64) DEFAULT NULL,
  `payment_method` varchar(50) DEFAULT NULL,
  `pdf_url` varchar(500) DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `payment_direction` enum('CUSTOMER','VENDOR') DEFAULT NULL,
  `purchase_order_id` bigint DEFAULT NULL,
  `archived` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKn939tpje3kshdfk6pbi7ewwj7` (`payment_code`),
  KEY `FKd7gx3doh12b2qx2b9j2e1dsxe` (`company_id`),
  CONSTRAINT `FKd7gx3doh12b2qx2b9j2e1dsxe` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `payroll` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bank_account` varchar(100) NOT NULL,
  `bank_name` varchar(200) NOT NULL,
  `deductions` double DEFAULT NULL,
  `gross_pay` double DEFAULT NULL,
  `loan_deduction` double DEFAULT NULL,
  `net_payable` double DEFAULT NULL,
  `pay_date` date DEFAULT NULL,
  `pay_period_end` date DEFAULT NULL,
  `pay_period_start` date DEFAULT NULL,
  `payroll_code` varchar(100) NOT NULL,
  `employee_id` bigint NOT NULL,
  `lop_amount` double NOT NULL,
  `lop_days` double NOT NULL,
  `paid_leave_days` double NOT NULL,
  `payable_days` double NOT NULL,
  `unpaid_leave_days` double NOT NULL,
  `worked_days` double NOT NULL,
  `worked_hours` double NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payroll_employee_period` (`employee_id`,`pay_period_start`,`pay_period_end`),
  CONSTRAINT `FKo65c0oqf6hr6eka6xtty7ccc` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `purchase_requisitions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approved_at` datetime(6) DEFAULT NULL,
  `converted_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `requisition_number` varchar(255) NOT NULL,
  `status` enum('APPROVED','CONVERTED','DRAFT','REJECTED','SUBMITTED') NOT NULL,
  `approved_by` bigint DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `converted_by` bigint DEFAULT NULL,
  `requested_by` bigint DEFAULT NULL,
  `department_id` bigint DEFAULT NULL,
  `preferred_supplier_id` bigint DEFAULT NULL,
  `finance_transaction_id` bigint DEFAULT NULL,
  `credit_account_id` bigint DEFAULT NULL,
  `debit_account_id` bigint DEFAULT NULL,
  `archived` bit(1) NOT NULL,
  `supplier_address` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmbapdku3ky7gprvf6prb1uvoi` (`approved_by`),
  KEY `FK6hcffwm6ovm9fcajkl79u95w9` (`company_id`),
  KEY `FKi7xwknvbmpsiq4eia0bpw27lc` (`converted_by`),
  KEY `FK6ablq6oc8aqwv25t9gd23j1hj` (`requested_by`),
  KEY `FKbed1cmmugaqjx6yinalsyijpa` (`department_id`),
  KEY `FK3rluf4ulxp1e3otqsqnquex11` (`preferred_supplier_id`),
  KEY `FKr975vxx6giqn9wt2f9bsq3pbq` (`credit_account_id`),
  KEY `FK210oxl5pp2bniqhypja62fv6s` (`debit_account_id`),
  CONSTRAINT `FK210oxl5pp2bniqhypja62fv6s` FOREIGN KEY (`debit_account_id`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FK3rluf4ulxp1e3otqsqnquex11` FOREIGN KEY (`preferred_supplier_id`) REFERENCES `vendor` (`id`),
  CONSTRAINT `FK6ablq6oc8aqwv25t9gd23j1hj` FOREIGN KEY (`requested_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FK6hcffwm6ovm9fcajkl79u95w9` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKbed1cmmugaqjx6yinalsyijpa` FOREIGN KEY (`department_id`) REFERENCES `departments` (`id`),
  CONSTRAINT `FKi7xwknvbmpsiq4eia0bpw27lc` FOREIGN KEY (`converted_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKmbapdku3ky7gprvf6prb1uvoi` FOREIGN KEY (`approved_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKr975vxx6giqn9wt2f9bsq3pbq` FOREIGN KEY (`credit_account_id`) REFERENCES `chart_of_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `purchase_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `order_date` date NOT NULL,
  `order_number` varchar(255) NOT NULL,
  `status` enum('CANCELLED','CONFIRMED','DRAFT','PARTIALLY_RECEIVED','RECEIVED') NOT NULL,
  `total_amount` decimal(18,2) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `source_requisition_id` bigint DEFAULT NULL,
  `supplier_id` bigint NOT NULL,
  `archived` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4vayf1gjopbx9r2352vu74a0q` (`company_id`,`order_number`),
  KEY `FKcgqvocxciov7fq8tev9p50e5d` (`created_by`),
  KEY `FKmjhhm8bt9or0tmj1k8k1p4ib2` (`source_requisition_id`),
  KEY `FKqa8jcb37par8ewlj340s4iw39` (`supplier_id`),
  CONSTRAINT `FK5x3y5dyxkl97drveotd2ckcwf` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKcgqvocxciov7fq8tev9p50e5d` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKmjhhm8bt9or0tmj1k8k1p4ib2` FOREIGN KEY (`source_requisition_id`) REFERENCES `purchase_requisitions` (`id`),
  CONSTRAINT `FKqa8jcb37par8ewlj340s4iw39` FOREIGN KEY (`supplier_id`) REFERENCES `vendor` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `purchase_order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `line_total` decimal(18,2) NOT NULL,
  `quantity` int NOT NULL,
  `unit_cost` decimal(18,2) NOT NULL,
  `item_id` bigint NOT NULL,
  `purchase_order_id` bigint DEFAULT NULL,
  `actual_item_price` decimal(18,2) DEFAULT NULL,
  `other_unit_cost` decimal(18,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKohkm3h7y9fnaj5h9g2wf5sblf` (`item_id`),
  KEY `FKo3yj8ocbw2kav38548t22hgh8` (`purchase_order_id`),
  CONSTRAINT `FKo3yj8ocbw2kav38548t22hgh8` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_orders` (`id`),
  CONSTRAINT `FKohkm3h7y9fnaj5h9g2wf5sblf` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `purchase_requisition_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `remarks` varchar(255) DEFAULT NULL,
  `requested_qty` int DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `requisition_id` bigint DEFAULT NULL,
  `estimated_unit_cost` decimal(18,2) DEFAULT NULL,
  `actual_item_price` decimal(18,2) DEFAULT NULL,
  `other_unit_cost` decimal(18,2) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKpsbs6wcvl6c5xw31t2tssrjch` (`item_id`),
  KEY `FKs8cxt7ys60906wvji3yc353pl` (`requisition_id`),
  CONSTRAINT `FKpsbs6wcvl6c5xw31t2tssrjch` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`),
  CONSTRAINT `FKs8cxt7ys60906wvji3yc353pl` FOREIGN KEY (`requisition_id`) REFERENCES `purchase_requisitions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `goods_receipts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `received_at` datetime(6) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `purchase_order_id` bigint NOT NULL,
  `received_by` bigint DEFAULT NULL,
  `document_pdf_url` varchar(1024) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKc25lsif7fssd9x0ane1hhdjes` (`company_id`),
  KEY `FK31kyyaqb354qfc4pssihmmry5` (`purchase_order_id`),
  KEY `FKtl5br0c9ehcok649rb7ijjyu5` (`received_by`),
  CONSTRAINT `FK31kyyaqb354qfc4pssihmmry5` FOREIGN KEY (`purchase_order_id`) REFERENCES `purchase_orders` (`id`),
  CONSTRAINT `FKc25lsif7fssd9x0ane1hhdjes` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKtl5br0c9ehcok649rb7ijjyu5` FOREIGN KEY (`received_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `goods_receipt_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `accepted_qty` int DEFAULT NULL,
  `received_qty` int DEFAULT NULL,
  `rejected_qty` int DEFAULT NULL,
  `remarks` varchar(255) DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `goods_receipt_id` bigint DEFAULT NULL,
  `warehouse_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcps9vgbt3m9jx12qfvd8v2f73` (`item_id`),
  KEY `FKel2i3xb291r13xh5o28hwhqc6` (`goods_receipt_id`),
  KEY `FKpxy3n6f9b6e1mvs86mmpe9v5i` (`warehouse_id`),
  CONSTRAINT `FKcps9vgbt3m9jx12qfvd8v2f73` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`),
  CONSTRAINT `FKel2i3xb291r13xh5o28hwhqc6` FOREIGN KEY (`goods_receipt_id`) REFERENCES `goods_receipts` (`id`),
  CONSTRAINT `FKpxy3n6f9b6e1mvs86mmpe9v5i` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `reconciliations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(18,2) NOT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `initial_balance` decimal(18,2) NOT NULL,
  `new_balance` decimal(18,2) NOT NULL,
  `reason` varchar(255) DEFAULT NULL,
  `resource` varchar(255) DEFAULT NULL,
  `status` enum('CONFIRMED','DRAFT') NOT NULL,
  `as_of_date` datetime(6) DEFAULT NULL,
  `ending_balance` decimal(18,2) DEFAULT NULL,
  `reconcile_account` varchar(255) DEFAULT NULL,
  `account_id` bigint NOT NULL,
  `company_id` bigint NOT NULL,
  `confirmed_by` bigint DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKlwb43pchcuy11mo97myie2hka` (`account_id`),
  KEY `FK9cl19g8v7olkjgw8fv1ixycbh` (`company_id`),
  KEY `FKqadnl032fbwe1hvhcjab1yv5m` (`confirmed_by`),
  KEY `FKpwuch52axpdtbbcwd767mydcg` (`created_by`),
  KEY `FK662ebba01tdrotm180oldijj4` (`updated_by`),
  CONSTRAINT `FK662ebba01tdrotm180oldijj4` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FK9cl19g8v7olkjgw8fv1ixycbh` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FKlwb43pchcuy11mo97myie2hka` FOREIGN KEY (`account_id`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FKpwuch52axpdtbbcwd767mydcg` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKqadnl032fbwe1hvhcjab1yv5m` FOREIGN KEY (`confirmed_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `role_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approve` bit(1) NOT NULL,
  `create_permission` bit(1) NOT NULL,
  `delete_permission` bit(1) NOT NULL,
  `edit_permission` bit(1) NOT NULL,
  `module` enum('APPRAISAL','CURRENT_JOB','DEPENDENTS','EMPLOYEE_PROFILE','HR_REPORTS','HR_SETTINGS','IMMIGRATION','LEAVES','LOANS','SALARY') NOT NULL,
  `role` varchar(100) NOT NULL,
  `view_all` bit(1) NOT NULL,
  `view_own` bit(1) NOT NULL,
  `employee_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_module` (`role`,`module`),
  UNIQUE KEY `uk_employee_module` (`employee_id`,`module`),
  CONSTRAINT `FKd007yy79sn13er80dg578br3n` FOREIGN KEY (`employee_id`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `salary_allowances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(38,2) NOT NULL,
  `custom_name` varchar(255) DEFAULT NULL,
  `effective_date` date DEFAULT NULL,
  `note` varchar(255) DEFAULT NULL,
  `allowance_type_id` bigint DEFAULT NULL,
  `contract_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKb818wad52bhalknirf0gg9g4p` (`allowance_type_id`),
  KEY `FKo9agpfjrjewmqkrebptfjpxvn` (`contract_id`),
  CONSTRAINT `FKb818wad52bhalknirf0gg9g4p` FOREIGN KEY (`allowance_type_id`) REFERENCES `allowance_types` (`id`),
  CONSTRAINT `FKo9agpfjrjewmqkrebptfjpxvn` FOREIGN KEY (`contract_id`) REFERENCES `contracts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sales` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `discount` decimal(38,2) DEFAULT NULL,
  `item_code` varchar(255) DEFAULT NULL,
  `product_name` varchar(255) DEFAULT NULL,
  `quantity` decimal(38,2) DEFAULT NULL,
  `total` decimal(38,2) DEFAULT NULL,
  `total_due` decimal(38,2) DEFAULT NULL,
  `unit_price` decimal(38,2) DEFAULT NULL,
  `order_no` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKr3bamovpm1idbw2br3hgu8kd4` (`order_no`),
  CONSTRAINT `FKr3bamovpm1idbw2br3hgu8kd4` FOREIGN KEY (`order_no`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sales_orders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `order_date` date NOT NULL,
  `order_number` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  `total_amount` decimal(18,2) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `customer_id` bigint NOT NULL,
  `invoice_due_date` date NOT NULL,
  `bank_account_id` bigint NOT NULL,
  `credit_account_id` bigint NOT NULL,
  `debit_account_id` bigint NOT NULL,
  `discount_amount` decimal(18,2) DEFAULT NULL,
  `subtotal_amount` decimal(18,2) DEFAULT NULL,
  `tax_amount` decimal(18,2) DEFAULT NULL,
  `shipping_address` varchar(1000) DEFAULT NULL,
  `archived` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK710tqn7k0rkp3ubriqlh0woyp` (`order_number`),
  KEY `FK42xgx5uj13us52n10r35soe8l` (`company_id`),
  KEY `FK67f7uxfsohlm19f8sp226ckko` (`created_by`),
  KEY `FKfs1owechmxg3lvej5vq1s8t8i` (`customer_id`),
  KEY `FKsw7lflwbpxqbn2cpiiun01g40` (`bank_account_id`),
  KEY `FKm2y5p8g9opfp9oe17bhqnawnq` (`credit_account_id`),
  KEY `FKn14khb5wt28pb2lbhw0f2bo3d` (`debit_account_id`),
  CONSTRAINT `FK42xgx5uj13us52n10r35soe8l` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`),
  CONSTRAINT `FK67f7uxfsohlm19f8sp226ckko` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKfs1owechmxg3lvej5vq1s8t8i` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`),
  CONSTRAINT `FKm2y5p8g9opfp9oe17bhqnawnq` FOREIGN KEY (`credit_account_id`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FKn14khb5wt28pb2lbhw0f2bo3d` FOREIGN KEY (`debit_account_id`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FKsw7lflwbpxqbn2cpiiun01g40` FOREIGN KEY (`bank_account_id`) REFERENCES `bank_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sales_order_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `line_total` decimal(18,2) DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `unit_price` decimal(18,2) DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `sales_order_id` bigint DEFAULT NULL,
  `discount_percent` decimal(18,2) DEFAULT NULL,
  `line_subtotal` decimal(18,2) DEFAULT NULL,
  `tax_amount` decimal(18,2) DEFAULT NULL,
  `tax_rate` decimal(18,2) DEFAULT NULL,
  `warehouse_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKq335fyx4llay1o4xh1p7tie2g` (`item_id`),
  KEY `FKtrge001xfy0fc9961g11411re` (`sales_order_id`),
  KEY `FK16opb6cxl66ui4b7g76gd46e9` (`warehouse_id`),
  CONSTRAINT `FK16opb6cxl66ui4b7g76gd46e9` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`),
  CONSTRAINT `FKq335fyx4llay1o4xh1p7tie2g` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`),
  CONSTRAINT `FKtrge001xfy0fc9961g11411re` FOREIGN KEY (`sales_order_id`) REFERENCES `sales_orders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `picklists` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `picklist_number` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  `company_id` bigint NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `sales_order_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKtd8setx5r7gble8q527k14wt5` (`picklist_number`),
  KEY `FKqmhvgn6vaxcwnse8v5eejcy1s` (`company_id`),
  KEY `FKd3uryu4xaopy94tl44tg5f8br` (`created_by`),
  KEY `FKjp5xovfvi441kmqt7muml9j0d` (`sales_order_id`),
  CONSTRAINT `FKd3uryu4xaopy94tl44tg5f8br` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FKjp5xovfvi441kmqt7muml9j0d` FOREIGN KEY (`sales_order_id`) REFERENCES `sales_orders` (`id`),
  CONSTRAINT `FKqmhvgn6vaxcwnse8v5eejcy1s` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `picklist_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantity` int DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `picklist_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsy4nncqbxl8fox2l38rbjt838` (`item_id`),
  KEY `FKif2g6ues41vp6jn2wlwlx96m0` (`picklist_id`),
  CONSTRAINT `FKif2g6ues41vp6jn2wlwlx96m0` FOREIGN KEY (`picklist_id`) REFERENCES `picklists` (`id`),
  CONSTRAINT `FKsy4nncqbxl8fox2l38rbjt838` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `shipments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `carrier_name` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `dispatched_at` datetime(6) DEFAULT NULL,
  `shipment_number` varchar(255) NOT NULL,
  `status` varchar(255) DEFAULT NULL,
  `tracking_number` varchar(255) DEFAULT NULL,
  `company_id` bigint NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `customer_id` bigint NOT NULL,
  `picklist_id` bigint NOT NULL,
  `delivery_address` varchar(255) DEFAULT NULL,
  `driver_name` varchar(255) DEFAULT NULL,
  `driver_phone` varchar(255) DEFAULT NULL,
  `estimated_delivery_date` varchar(255) DEFAULT NULL,
  `failed_delivery_at` datetime(6) DEFAULT NULL,
  `in_transit_at` datetime(6) DEFAULT NULL,
  `notes` varchar(255) DEFAULT NULL,
  `out_for_delivery_at` datetime(6) DEFAULT NULL,
  `vehicle_number` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKil6gfafk66ly6rpmjugdmd1ne` (`shipment_number`),
  KEY `FK8v7drlhmwaw34phw6rf5lv1rk` (`company_id`),
  KEY `FK70ogx8e494qwx97n8g4tl3bka` (`created_by`),
  KEY `FK4riardtm02dseyjfo3vjrg3o0` (`customer_id`),
  KEY `FK26kwx5p1vbpady62g5frv1lnc` (`picklist_id`),
  CONSTRAINT `FK26kwx5p1vbpady62g5frv1lnc` FOREIGN KEY (`picklist_id`) REFERENCES `picklists` (`id`),
  CONSTRAINT `FK4riardtm02dseyjfo3vjrg3o0` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`),
  CONSTRAINT `FK70ogx8e494qwx97n8g4tl3bka` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`),
  CONSTRAINT `FK8v7drlhmwaw34phw6rf5lv1rk` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `shipment_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `quantity` int DEFAULT NULL,
  `item_id` bigint NOT NULL,
  `shipment_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1q9jkbfsufk6jse1e3d5iqaip` (`item_id`),
  KEY `FK4rh14gyym63tnsi2i95f61d7` (`shipment_id`),
  CONSTRAINT `FK1q9jkbfsufk6jse1e3d5iqaip` FOREIGN KEY (`item_id`) REFERENCES `items` (`id`),
  CONSTRAINT `FK4rh14gyym63tnsi2i95f61d7` FOREIGN KEY (`shipment_id`) REFERENCES `shipments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `shipment_tracking_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `event_at` datetime(6) NOT NULL,
  `location` varchar(255) DEFAULT NULL,
  `notes` varchar(1000) DEFAULT NULL,
  `status` varchar(255) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `shipment_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKs9rcbu8v04hknkj5s6tbllj6r` (`created_by`),
  KEY `FK721m3h0qgg9ihf5osrm4dchef` (`shipment_id`),
  CONSTRAINT `FK721m3h0qgg9ihf5osrm4dchef` FOREIGN KEY (`shipment_id`) REFERENCES `shipments` (`id`),
  CONSTRAINT `FKs9rcbu8v04hknkj5s6tbllj6r` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `settlements` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(38,2) DEFAULT NULL,
  `debit_account` varchar(255) DEFAULT NULL,
  `payment_method` varchar(255) DEFAULT NULL,
  `settlement_account` varchar(255) DEFAULT NULL,
  `settlement_code` varchar(255) DEFAULT NULL,
  `settlement_date` datetime(6) DEFAULT NULL,
  `settlement_type` varchar(255) DEFAULT NULL,
  `total_due` decimal(18,2) DEFAULT NULL,
  `transaction_date` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `transactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(18,2) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `invoice_id` varchar(64) DEFAULT NULL,
  `payment_id` varchar(64) DEFAULT NULL,
  `related_id` bigint DEFAULT NULL,
  `related_sub_id` bigint DEFAULT NULL,
  `source` varchar(64) DEFAULT NULL,
  `source_locked` bit(1) NOT NULL,
  `transaction_code` varchar(64) DEFAULT NULL,
  `transaction_date` date DEFAULT NULL,
  `transaction_description` varchar(500) DEFAULT NULL,
  `transaction_type` varchar(50) DEFAULT NULL,
  `company_id` bigint DEFAULT NULL,
  `credit_account` bigint DEFAULT NULL,
  `debit_account` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhmrka43k7ijk56xcfcnscaq14` (`transaction_code`),
  KEY `FKeyusbb454wshg7tc93rvfkvte` (`company_id`),
  KEY `FK78h9a4t91yp17d6y2kjkexln4` (`credit_account`),
  KEY `FK9eicixaxwpg1ggrqn472d6tx8` (`debit_account`),
  CONSTRAINT `FK78h9a4t91yp17d6y2kjkexln4` FOREIGN KEY (`credit_account`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FK9eicixaxwpg1ggrqn472d6tx8` FOREIGN KEY (`debit_account`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FKeyusbb454wshg7tc93rvfkvte` FOREIGN KEY (`company_id`) REFERENCES `companies` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `transfers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `transfer_amount` decimal(38,2) DEFAULT NULL,
  `transfer_code` varchar(255) DEFAULT NULL,
  `transfer_date` datetime(6) DEFAULT NULL,
  `transfer_reason` varchar(255) DEFAULT NULL,
  `transfer_type` varchar(255) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `credit_account` bigint DEFAULT NULL,
  `debit_account` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKdl9cmt36ydar7t07ak2npcce` (`transfer_code`),
  KEY `FKcgu948tytrf70x100bnlo0cko` (`created_by`),
  KEY `FK682xy0irax8n6i3nvqjfxvyw0` (`credit_account`),
  KEY `FK822d7yi6nnx3gosb2i3ou7eb2` (`debit_account`),
  CONSTRAINT `FK682xy0irax8n6i3nvqjfxvyw0` FOREIGN KEY (`credit_account`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FK822d7yi6nnx3gosb2i3ou7eb2` FOREIGN KEY (`debit_account`) REFERENCES `chart_of_accounts` (`id`),
  CONSTRAINT `FKcgu948tytrf70x100bnlo0cko` FOREIGN KEY (`created_by`) REFERENCES `employees` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `variances` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `from_location` varchar(255) DEFAULT NULL,
  `item_code` varchar(255) DEFAULT NULL,
  `notes_remarks` varchar(255) DEFAULT NULL,
  `to_location` varchar(255) DEFAULT NULL,
  `variance_date` datetime(6) DEFAULT NULL,
  `variance_quantity` decimal(38,2) DEFAULT NULL,
  `variance_reason` varchar(255) DEFAULT NULL,
  `variance_status` varchar(255) DEFAULT NULL,
  `variance_type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
