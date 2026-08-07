package com.erp.service.subscription;

import com.erp.domain.hr.Company;
import com.erp.domain.subscription.CompanySubscription;
import com.erp.domain.subscription.SubscriptionReminderLog;
import com.erp.domain.subscription.SubscriptionReminderType;
import com.erp.domain.subscription.SubscriptionStatus;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.subscription.CompanySubscriptionRepository;
import com.erp.repo.subscription.SubscriptionReminderLogRepository;
import com.erp.service.notification.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionReminderJob {

    private final CompanySubscriptionRepository subscriptionRepository;
    private final SubscriptionReminderLogRepository reminderLogRepository;
    private final CompanyRepository companyRepository;
    private final EmailService emailService;
    private final SubscriptionService subscriptionService;

    @Value("${app.subscription.alert-email:}")
    private String alertEmail;

    @Scheduled(cron = "0 20 0 * * *")
    @Transactional
    public void sendReminders() {
        LocalDate today = LocalDate.now();
        // Ensure statuses are current before choosing reminder type
        subscriptionService.reconcileStatuses();

        for (CompanySubscription cs : subscriptionRepository.findAllWithEndDateForReconcile()) {
            if (cs.getEndsAt() == null) continue;
            if (cs.getStatus() == SubscriptionStatus.CANCELLED || cs.getStatus() == SubscriptionStatus.SUSPENDED) {
                continue;
            }

            long daysLeft = ChronoUnit.DAYS.between(today, cs.getEndsAt());
            SubscriptionReminderType type = resolveType(daysLeft, cs.getStatus());
            if (type == null) continue;

            String periodKey = cs.getEndsAt().toString();
            if (reminderLogRepository.existsByCompanySubscriptionIdAndReminderTypeAndPeriodKeyAndSuccessTrue(
                    cs.getId(), type, periodKey)) {
                continue;
            }

            Company company = companyRepository.findById(cs.getCompanyId()).orElse(null);
            String to = resolveRecipient(company);
            if (to == null || to.isBlank()) {
                saveLog(cs.getId(), type, periodKey, null, false, "No billing/company email");
                continue;
            }

            try {
                String subject = buildSubject(type, company);
                String body = buildBody(type, company, cs, daysLeft);
                emailService.sendPlainText(to, subject, body);

                if (type == SubscriptionReminderType.EXPIRED
                        && alertEmail != null && !alertEmail.isBlank()) {
                    try {
                        emailService.sendPlainText(
                                alertEmail.trim(),
                                "[Platform] Subscription expired: " + (company != null ? company.getCompanyName() : cs.getCompanyId()),
                                body
                        );
                    } catch (Exception alertEx) {
                        log.warn("Failed to notify alert email: {}", alertEx.getMessage());
                    }
                }

                saveLog(cs.getId(), type, periodKey, to, true, null);
            } catch (Exception ex) {
                log.warn("Subscription reminder failed companyId={}: {}", cs.getCompanyId(), ex.getMessage());
                saveLog(cs.getId(), type, periodKey, to, false, truncate(ex.getMessage()));
            }
        }
    }

    private SubscriptionReminderType resolveType(long daysLeft, SubscriptionStatus status) {
        if (daysLeft < 0 || status == SubscriptionStatus.EXPIRED) {
            return SubscriptionReminderType.EXPIRED;
        }
        if (daysLeft == 0) return SubscriptionReminderType.DAY_OF;
        if (daysLeft == 1) return SubscriptionReminderType.D1;
        if (daysLeft == 3) return SubscriptionReminderType.D3;
        if (daysLeft == 7) return SubscriptionReminderType.D7;
        return null;
    }

    private String resolveRecipient(Company company) {
        if (company == null) return null;
        if (company.getBillingEmail() != null && !company.getBillingEmail().isBlank()) {
            return company.getBillingEmail().trim();
        }
        if (company.getCompanyEmail() != null && !company.getCompanyEmail().isBlank()) {
            return company.getCompanyEmail().trim();
        }
        return null;
    }

    private String buildSubject(SubscriptionReminderType type, Company company) {
        String name = company != null ? company.getCompanyName() : "Your company";
        return switch (type) {
            case D7 -> name + " — subscription expires in 7 days";
            case D3 -> name + " — subscription expires in 3 days";
            case D1 -> name + " — subscription expires tomorrow";
            case DAY_OF -> name + " — subscription expires today";
            case EXPIRED -> name + " — subscription expired; access suspended";
        };
    }

    private String buildBody(SubscriptionReminderType type, Company company, CompanySubscription cs, long daysLeft) {
        String name = company != null ? company.getCompanyName() : ("Company #" + cs.getCompanyId());
        StringBuilder sb = new StringBuilder();
        sb.append("Hello,\n\n");
        sb.append("Company: ").append(name).append("\n");
        sb.append("Plan: ").append(cs.getPlanType()).append("\n");
        if (cs.getAmount() != null) {
            sb.append("Amount: ").append(cs.getAmount());
            if (cs.getCurrencyCode() != null) sb.append(' ').append(cs.getCurrencyCode());
            sb.append('\n');
        }
        sb.append("End date: ").append(cs.getEndsAt()).append('\n');
        if (type != SubscriptionReminderType.EXPIRED) {
            sb.append("Days remaining: ").append(Math.max(daysLeft, 0)).append('\n');
        }
        sb.append('\n');
        if (type == SubscriptionReminderType.EXPIRED) {
            sb.append("Access to the ERP for this company has been suspended because the subscription expired.\n");
            sb.append("Please contact your platform administrator to renew (offline payment).\n");
        } else {
            sb.append("Please arrange offline payment with your platform administrator before the end date to avoid interruption.\n");
        }
        sb.append("\n— Sunway ERP\n");
        return sb.toString();
    }

    private void saveLog(
            Long subscriptionId,
            SubscriptionReminderType type,
            String periodKey,
            String toEmail,
            boolean success,
            String error
    ) {
        reminderLogRepository.save(SubscriptionReminderLog.builder()
                .companySubscriptionId(subscriptionId)
                .reminderType(type)
                .periodKey(periodKey)
                .sentAt(Instant.now())
                .toEmail(toEmail)
                .success(success)
                .error(error)
                .build());
    }

    private static String truncate(String msg) {
        if (msg == null) return null;
        return msg.length() > 900 ? msg.substring(0, 900) : msg;
    }
}
