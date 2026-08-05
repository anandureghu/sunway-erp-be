package com.erp.config;

import com.erp.domain.hr.Company;
import com.erp.repo.hr.CompanyRepository;
import com.erp.service.hr.QatarLaborLawDefaultsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-shot: after Flyway adds labor-law columns, force-apply Qatar leave matrix
 * + balance sync for every company. Subsequent startups are no-ops.
 */
@Component
@Order(50)
public class QatarLaborLawDefaultsInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QatarLaborLawDefaultsInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final CompanyRepository companyRepository;
    private final QatarLaborLawDefaultsService qatarLaborLawDefaultsService;

    public QatarLaborLawDefaultsInitializer(
            JdbcTemplate jdbcTemplate,
            CompanyRepository companyRepository,
            QatarLaborLawDefaultsService qatarLaborLawDefaultsService) {
        this.jdbcTemplate = jdbcTemplate;
        this.companyRepository = companyRepository;
        this.qatarLaborLawDefaultsService = qatarLaborLawDefaultsService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer done = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM one_time_tasks WHERE task_key = ?",
                Integer.class,
                QatarLaborLawDefaultsService.ONE_TIME_TASK_KEY);
        if (done != null && done > 0) {
            return;
        }

        List<Company> companies = companyRepository.findAll();
        log.info("Applying Qatar labor-law leave defaults to {} companies", companies.size());
        for (Company company : companies) {
            try {
                qatarLaborLawDefaultsService.applyLeaveDefaults(company.getId());
            } catch (Exception e) {
                log.error("Failed to apply Qatar leave defaults for company {}", company.getId(), e);
            }
        }

        jdbcTemplate.update(
                "INSERT INTO one_time_tasks (task_key, completed_at) VALUES (?, CURRENT_TIMESTAMP)",
                QatarLaborLawDefaultsService.ONE_TIME_TASK_KEY);
        log.info("Qatar labor-law leave defaults bootstrap complete");
    }
}
