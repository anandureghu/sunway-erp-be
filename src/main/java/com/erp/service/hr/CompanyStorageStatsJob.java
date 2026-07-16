package com.erp.service.hr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Nightly refresh of each company's cached database-storage estimate (see CompanyStorageService). */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyStorageStatsJob {

    private final CompanyStorageService companyStorageService;

    @Scheduled(cron = "0 30 2 * * *")
    public void refreshStorageStats() {
        log.info("Refreshing company database storage stats");
        companyStorageService.recalculateAllCompanies();
    }
}
