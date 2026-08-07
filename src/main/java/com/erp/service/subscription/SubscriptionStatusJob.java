package com.erp.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionStatusJob {

    private final SubscriptionService subscriptionService;

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        try {
            subscriptionService.reconcileStatuses();
        } catch (Exception ex) {
            log.warn("Subscription status reconcile on startup failed: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "0 15 0 * * *")
    @Transactional
    public void reconcileDaily() {
        subscriptionService.reconcileStatuses();
    }
}
