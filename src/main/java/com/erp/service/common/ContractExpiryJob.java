package com.erp.service.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Ends contracts that have run out. Any still-ACTIVE contract whose expiration date
 * has passed is marked EXPIRED and its employee is stood down (status INACTIVE with
 * the "Non-renewal of contract" termination code). Runs daily and once on startup so
 * a contract that lapses while the service is down is caught on the next boot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractExpiryJob {

    private final ContractService contractService;

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void sweepOnStartup() {
        sweepLapsedContracts();
    }

    @Transactional
    @Scheduled(cron = "0 10 0 * * *") // every day at 00:10
    public void sweepLapsedContracts() {
        int ended = contractService.sweepLapsedContracts(LocalDate.now());
        if (ended > 0) {
            log.info("Contract-expiry sweep ended {} lapsed contract(s)", ended);
        }
    }
}
