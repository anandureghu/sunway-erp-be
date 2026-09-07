package com.erp.service.hr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Runs monthly EOSB accruals on the 1st at 01:00 (posts for the prior month).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EndOfServiceAccrualJob {

    private final EndOfServiceAccrualService endOfServiceAccrualService;

    @Scheduled(cron = "0 0 1 1 * *")
    public void accruePreviousMonth() {
        LocalDate previousMonth = LocalDate.now().minusMonths(1);
        try {
            int posted = endOfServiceAccrualService.accrueForMonth(previousMonth);
            log.info("EOSB monthly accrual for {} posted {} employee entries", previousMonth, posted);
        } catch (Exception ex) {
            log.error("EOSB monthly accrual failed for {}: {}", previousMonth, ex.getMessage(), ex);
        }
    }
}
