package com.erp.service.subscription;

import com.erp.domain.hr.Company;
import com.erp.domain.security.Role;
import com.erp.domain.subscription.*;
import com.erp.dto.subscription.*;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.subscription.CompanySubscriptionRepository;
import com.erp.repo.subscription.SubscriptionPaymentRepository;
import com.erp.repo.subscription.SubscriptionReminderLogRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.security.CustomUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final CompanySubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentRepository paymentRepository;
    private final SubscriptionReminderLogRepository reminderLogRepository;
    private final CompanyRepository companyRepository;
    private final AuthContext authContext;

    @Transactional(readOnly = true)
    public Page<CompanySubscriptionResponse> list(
            SubscriptionStatus status,
            SubscriptionPlanType planType,
            Long companyId,
            Integer expiringWithinDays,
            Pageable pageable
    ) {
        LocalDate expiringBefore = expiringWithinDays != null
                ? LocalDate.now().plusDays(expiringWithinDays)
                : null;
        return subscriptionRepository.search(status, planType, companyId, expiringBefore, pageable)
                .map(cs -> toListItem(cs, false));
    }

    @Transactional(readOnly = true)
    public CompanySubscriptionResponse getByCompanyId(Long companyId) {
        CompanySubscription cs = requireSubscription(companyId);
        return toDetail(cs);
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getMyStatus() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            return SubscriptionStatusResponse.builder()
                    .locked(false)
                    .showWarningBanner(false)
                    .build();
        }
        return toStatusResponse(companyId, isSuperAdmin());
    }

    @Transactional(readOnly = true)
    public SubscriptionStatusResponse getStatusForCompany(Long companyId, boolean includeAmount) {
        return toStatusResponse(companyId, includeAmount);
    }

    /**
     * Returns true when non–SUPER_ADMIN users must be hard-locked out of this company.
     */
    @Transactional(readOnly = true)
    public boolean isAccessLocked(Long companyId) {
        if (companyId == null) return false;
        return subscriptionRepository.findByCompanyId(companyId)
                .map(this::computeLocked)
                .orElse(false);
    }

    @Transactional
    public CompanySubscriptionResponse assign(Long companyId, AssignSubscriptionRequest req) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        validateAssign(req);

        LocalDate startsAt = req.getStartsAt();
        LocalDate endsAt = resolveEndsAt(req.getPlanType(), startsAt, req.getEndsAt());

        CompanySubscription cs = subscriptionRepository.findByCompanyId(companyId).orElse(null);
        Instant now = Instant.now();
        String actor = currentActor();

        if (cs == null) {
            cs = CompanySubscription.builder()
                    .companyId(companyId)
                    .createdAt(now)
                    .createdBy(actor)
                    .build();
        }

        cs.setPlanType(req.getPlanType());
        cs.setAmount(req.getPlanType() == SubscriptionPlanType.FREE
                ? BigDecimal.ZERO
                : Optional.ofNullable(req.getAmount()).orElse(BigDecimal.ZERO));
        cs.setCurrencyCode(firstNonBlank(req.getCurrencyCode(),
                company.getCurrency() != null ? company.getCurrency().getCurrencyCode() : null));
        cs.setStartsAt(startsAt);
        cs.setEndsAt(endsAt);
        cs.setWarningDays(req.getWarningDays() != null ? req.getWarningDays() : 7);
        cs.setGraceDays(req.getGraceDays() != null ? req.getGraceDays() : 0);
        cs.setHrEntitled(req.getHrEntitled() == null || req.getHrEntitled());
        cs.setFinanceEntitled(req.getFinanceEntitled() == null || req.getFinanceEntitled());
        cs.setInventoryEntitled(req.getInventoryEntitled() == null || req.getInventoryEntitled());
        cs.setNotes(req.getNotes());
        cs.setUpdatedAt(now);
        cs.setUpdatedBy(actor);
        cs.setStatus(computeStatus(cs, LocalDate.now()));

        CompanySubscription saved = subscriptionRepository.save(cs);

        if (req.getSyncCompanyModules() == null || req.getSyncCompanyModules()) {
            company.setHrEnabled(saved.isHrEntitled());
            company.setFinanceEnabled(saved.isFinanceEntitled());
            company.setInventoryEnabled(saved.isInventoryEntitled());
            companyRepository.save(company);
        }

        log.info("Subscription assigned companyId={} plan={} status={} by={}",
                companyId, saved.getPlanType(), saved.getStatus(), actor);
        return toDetail(saved);
    }

    /** Seed FREE subscription for a newly created company. Idempotent. */
    @Transactional
    public void seedFreeForCompany(Company company) {
        if (company == null || company.getId() == null) return;
        if (subscriptionRepository.findByCompanyId(company.getId()).isPresent()) return;

        Instant now = Instant.now();
        CompanySubscription cs = CompanySubscription.builder()
                .companyId(company.getId())
                .planType(SubscriptionPlanType.FREE)
                .amount(BigDecimal.ZERO)
                .currencyCode(company.getCurrency() != null ? company.getCurrency().getCurrencyCode() : null)
                .startsAt(LocalDate.now())
                .endsAt(null)
                .status(SubscriptionStatus.ACTIVE)
                .warningDays(7)
                .graceDays(0)
                .hrEntitled(company.isHrEnabled())
                .financeEntitled(company.isFinanceEnabled())
                .inventoryEntitled(company.isInventoryEnabled())
                .createdAt(now)
                .updatedAt(now)
                .createdBy(currentActor())
                .build();
        subscriptionRepository.save(cs);
    }

    @Transactional
    public CompanySubscriptionResponse recordPayment(Long companyId, RecordSubscriptionPaymentRequest req) {
        CompanySubscription cs = requireSubscription(companyId);

        if (req.getIdempotencyKey() != null && !req.getIdempotencyKey().isBlank()) {
            Optional<SubscriptionPayment> existing = paymentRepository.findByIdempotencyKey(req.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                return toDetail(cs);
            }
        }

        boolean extend = req.getExtendSubscription() == null || req.getExtendSubscription();
        LocalDate periodStart = req.getPeriodStart() != null ? req.getPeriodStart() : LocalDate.now();
        LocalDate periodEnd = req.getPeriodEnd();
        if (extend && periodEnd == null) {
            periodEnd = autoExtendEnd(cs, LocalDate.now());
        }

        SubscriptionPayment payment = SubscriptionPayment.builder()
                .companySubscriptionId(cs.getId())
                .companyId(companyId)
                .amount(req.getAmount())
                .paidOn(req.getPaidOn())
                .methodNote(req.getMethodNote())
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .recordedBy(authContext.getCurrentUserId())
                .idempotencyKey(blankToNull(req.getIdempotencyKey()))
                .createdAt(Instant.now())
                .build();
        paymentRepository.save(payment);

        if (extend && periodEnd != null) {
            cs.setEndsAt(periodEnd);
            if (cs.getPlanType() == SubscriptionPlanType.FREE) {
                // keep FREE unless admin already changed plan
            }
            cs.setStatus(computeStatus(cs, LocalDate.now()));
            cs.setUpdatedAt(Instant.now());
            cs.setUpdatedBy(currentActor());
            subscriptionRepository.save(cs);
        }

        log.info("Subscription payment recorded companyId={} amount={} by={}",
                companyId, req.getAmount(), currentActor());
        return toDetail(cs);
    }

    @Transactional
    public CompanySubscriptionResponse extend(Long companyId, ExtendSubscriptionRequest req) {
        CompanySubscription cs = requireSubscription(companyId);
        if (req.getNewEndsAt().isBefore(LocalDate.now()) && cs.getPlanType() != SubscriptionPlanType.FREE) {
            throw new IllegalArgumentException("New end date must be today or in the future");
        }
        cs.setEndsAt(req.getNewEndsAt());
        if (req.getNotes() != null && !req.getNotes().isBlank()) {
            cs.setNotes(req.getNotes());
        }
        cs.setStatus(computeStatus(cs, LocalDate.now()));
        cs.setUpdatedAt(Instant.now());
        cs.setUpdatedBy(currentActor());
        subscriptionRepository.save(cs);
        log.info("Subscription extended companyId={} endsAt={} by={}", companyId, req.getNewEndsAt(), currentActor());
        return toDetail(cs);
    }

    @Transactional
    public CompanySubscriptionResponse cancel(Long companyId, CancelSubscriptionRequest req) {
        CompanySubscription cs = requireSubscription(companyId);
        SubscriptionStatus target = req.getStatus() != null ? req.getStatus() : SubscriptionStatus.CANCELLED;
        if (target != SubscriptionStatus.CANCELLED && target != SubscriptionStatus.SUSPENDED) {
            throw new IllegalArgumentException("Cancel status must be CANCELLED or SUSPENDED");
        }
        cs.setStatus(target);
        if (req.getNotes() != null && !req.getNotes().isBlank()) {
            cs.setNotes(req.getNotes());
        }
        cs.setUpdatedAt(Instant.now());
        cs.setUpdatedBy(currentActor());
        subscriptionRepository.save(cs);
        log.info("Subscription {} companyId={} by={}", target, companyId, currentActor());
        return toDetail(cs);
    }

    @Transactional(readOnly = true)
    public SubscriptionAnalyticsResponse analytics(LocalDate from, LocalDate to) {
        LocalDate rangeFrom = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate rangeTo = to != null ? to : LocalDate.now();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (SubscriptionStatus s : SubscriptionStatus.values()) {
            byStatus.put(s.name(), subscriptionRepository.countByStatus(s));
        }
        Map<String, Long> byPlan = new LinkedHashMap<>();
        for (SubscriptionPlanType p : SubscriptionPlanType.values()) {
            byPlan.put(p.name(), subscriptionRepository.countByPlanType(p));
        }

        BigDecimal revenue = paymentRepository.sumAmountBetween(rangeFrom, rangeTo);
        if (revenue == null) revenue = BigDecimal.ZERO;

        BigDecimal estimatedMrr = BigDecimal.ZERO;
        List<CompanySubscription> all = subscriptionRepository.findAll();
        for (CompanySubscription cs : all) {
            if (cs.getStatus() != SubscriptionStatus.ACTIVE && cs.getStatus() != SubscriptionStatus.EXPIRING) {
                continue;
            }
            if (cs.getPlanType() == SubscriptionPlanType.FREE) continue;
            BigDecimal amt = cs.getAmount() != null ? cs.getAmount() : BigDecimal.ZERO;
            if (cs.getPlanType() == SubscriptionPlanType.YEARLY) {
                estimatedMrr = estimatedMrr.add(amt.divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP));
            } else {
                estimatedMrr = estimatedMrr.add(amt);
            }
        }

        List<SubscriptionAnalyticsResponse.MonthlyRevenuePoint> monthly = new ArrayList<>();
        try {
            for (Object[] row : paymentRepository.sumByMonthBetween(rangeFrom, rangeTo)) {
                monthly.add(SubscriptionAnalyticsResponse.MonthlyRevenuePoint.builder()
                        .month(String.valueOf(row[0]))
                        .amount(row[1] instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(row[1])))
                        .build());
            }
        } catch (Exception ex) {
            log.warn("Monthly payment aggregation failed: {}", ex.getMessage());
        }

        long newInPeriod = all.stream()
                .filter(cs -> cs.getCreatedAt() != null
                        && !cs.getCreatedAt().isBefore(rangeFrom.atStartOfDay().toInstant(java.time.ZoneOffset.UTC))
                        && !cs.getCreatedAt().isAfter(rangeTo.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)))
                .count();
        long expiredInPeriod = all.stream()
                .filter(cs -> cs.getStatus() == SubscriptionStatus.EXPIRED)
                .filter(cs -> cs.getEndsAt() != null
                        && !cs.getEndsAt().isBefore(rangeFrom)
                        && !cs.getEndsAt().isAfter(rangeTo))
                .count();

        return SubscriptionAnalyticsResponse.builder()
                .totalCompanies(all.size())
                .countByStatus(byStatus)
                .countByPlanType(byPlan)
                .expiringIn7Days(subscriptionRepository.countExpiringBefore(LocalDate.now().plusDays(7)))
                .expiringIn30Days(subscriptionRepository.countExpiringBefore(LocalDate.now().plusDays(30)))
                .revenueCollectedInRange(revenue)
                .estimatedMonthlyRecurring(estimatedMrr)
                .newInPeriod(newInPeriod)
                .expiredInPeriod(expiredInPeriod)
                .paymentsByMonth(monthly)
                .build();
    }

    /** Daily reconcile: EXPIRING / EXPIRED transitions. */
    @Transactional
    public int reconcileStatuses() {
        LocalDate today = LocalDate.now();
        int updated = 0;
        for (CompanySubscription cs : subscriptionRepository.findAllWithEndDateForReconcile()) {
            if (cs.getStatus() == SubscriptionStatus.SUSPENDED) continue;
            SubscriptionStatus next = computeStatus(cs, today);
            if (next != cs.getStatus()) {
                cs.setStatus(next);
                cs.setUpdatedAt(Instant.now());
                subscriptionRepository.save(cs);
                updated++;
            }
        }
        // Also re-open FREE / open-ended if somehow stuck
        for (CompanySubscription cs : subscriptionRepository.findAll()) {
            if (cs.getPlanType() == SubscriptionPlanType.FREE && cs.getEndsAt() == null
                    && cs.getStatus() != SubscriptionStatus.CANCELLED
                    && cs.getStatus() != SubscriptionStatus.SUSPENDED
                    && cs.getStatus() != SubscriptionStatus.ACTIVE) {
                cs.setStatus(SubscriptionStatus.ACTIVE);
                cs.setUpdatedAt(Instant.now());
                subscriptionRepository.save(cs);
                updated++;
            }
        }
        log.info("Subscription status reconcile updated {} rows", updated);
        return updated;
    }

    // ── helpers ──────────────────────────────────────────────

    public SubscriptionStatus computeStatus(CompanySubscription cs, LocalDate today) {
        if (cs.getStatus() == SubscriptionStatus.CANCELLED || cs.getStatus() == SubscriptionStatus.SUSPENDED) {
            return cs.getStatus();
        }
        if (cs.getPlanType() == SubscriptionPlanType.FREE && cs.getEndsAt() == null) {
            return SubscriptionStatus.ACTIVE;
        }
        if (cs.getEndsAt() == null) {
            return SubscriptionStatus.ACTIVE;
        }
        if (cs.getEndsAt().isBefore(today)) {
            return SubscriptionStatus.EXPIRED;
        }
        long daysLeft = ChronoUnit.DAYS.between(today, cs.getEndsAt());
        if (daysLeft <= cs.getWarningDays()) {
            return SubscriptionStatus.EXPIRING;
        }
        return SubscriptionStatus.ACTIVE;
    }

    public boolean computeLocked(CompanySubscription cs) {
        if (cs == null) return false;
        if (cs.getStatus() == SubscriptionStatus.CANCELLED || cs.getStatus() == SubscriptionStatus.SUSPENDED) {
            return true;
        }
        if (cs.getStatus() == SubscriptionStatus.EXPIRED) {
            return true;
        }
        if (cs.getPlanType() != SubscriptionPlanType.FREE && cs.getEndsAt() != null
                && cs.getEndsAt().isBefore(LocalDate.now())) {
            return true;
        }
        return false;
    }

    private LocalDate autoExtendEnd(CompanySubscription cs, LocalDate from) {
        LocalDate base = cs.getEndsAt() != null && !cs.getEndsAt().isBefore(from)
                ? cs.getEndsAt()
                : from;
        return switch (cs.getPlanType()) {
            case MONTHLY -> base.plusMonths(1);
            case YEARLY -> base.plusYears(1);
            case CUSTOM, FREE -> base.plusMonths(1);
        };
    }

    private LocalDate resolveEndsAt(SubscriptionPlanType planType, LocalDate startsAt, LocalDate endsAt) {
        if (planType == SubscriptionPlanType.FREE) {
            return endsAt; // may be null (open-ended)
        }
        if (endsAt != null) {
            if (endsAt.isBefore(startsAt)) {
                throw new IllegalArgumentException("endsAt must be on or after startsAt");
            }
            return endsAt;
        }
        return switch (planType) {
            case MONTHLY -> startsAt.plusMonths(1);
            case YEARLY -> startsAt.plusYears(1);
            case CUSTOM -> throw new IllegalArgumentException("endsAt is required for CUSTOM plans");
            case FREE -> null;
        };
    }

    private void validateAssign(AssignSubscriptionRequest req) {
        if (req.getPlanType() != SubscriptionPlanType.FREE) {
            if (req.getAmount() == null || req.getAmount().signum() < 0) {
                throw new IllegalArgumentException("amount is required for paid plans");
            }
        }
    }

    private CompanySubscription requireSubscription(Long companyId) {
        return subscriptionRepository.findByCompanyId(companyId)
                .orElseThrow(() -> new IllegalArgumentException("No subscription for company " + companyId));
    }

    private SubscriptionStatusResponse toStatusResponse(Long companyId, boolean includeAmount) {
        Company company = companyRepository.findById(companyId).orElse(null);
        Optional<CompanySubscription> opt = subscriptionRepository.findByCompanyId(companyId);
        if (opt.isEmpty()) {
            return SubscriptionStatusResponse.builder()
                    .companyId(companyId)
                    .companyName(company != null ? company.getCompanyName() : null)
                    .planType(SubscriptionPlanType.FREE)
                    .status(SubscriptionStatus.ACTIVE)
                    .locked(false)
                    .showWarningBanner(false)
                    .billingContactEmail(billingEmail(company))
                    .build();
        }
        CompanySubscription cs = opt.get();
        Integer daysRemaining = daysRemaining(cs);
        boolean locked = computeLocked(cs);
        boolean showBanner = !locked
                && cs.getEndsAt() != null
                && daysRemaining != null
                && daysRemaining <= cs.getWarningDays()
                && (cs.getStatus() == SubscriptionStatus.ACTIVE || cs.getStatus() == SubscriptionStatus.EXPIRING);

        SubscriptionStatusResponse.SubscriptionStatusResponseBuilder b = SubscriptionStatusResponse.builder()
                .companyId(companyId)
                .companyName(company != null ? company.getCompanyName() : null)
                .planType(cs.getPlanType())
                .status(cs.getStatus())
                .startsAt(cs.getStartsAt())
                .endsAt(cs.getEndsAt())
                .warningDays(cs.getWarningDays())
                .daysRemaining(daysRemaining)
                .locked(locked)
                .showWarningBanner(showBanner)
                .billingContactEmail(billingEmail(company));

        if (includeAmount) {
            b.amount(cs.getAmount()).currencyCode(cs.getCurrencyCode());
        }
        return b.build();
    }

    private CompanySubscriptionResponse toListItem(CompanySubscription cs, boolean withHistory) {
        Company company = companyRepository.findById(cs.getCompanyId()).orElse(null);
        var lastPay = paymentRepository.findFirstByCompanyIdOrderByPaidOnDescCreatedAtDesc(cs.getCompanyId());

        CompanySubscriptionResponse.CompanySubscriptionResponseBuilder b = CompanySubscriptionResponse.builder()
                .id(cs.getId())
                .companyId(cs.getCompanyId())
                .companyName(company != null ? company.getCompanyName() : null)
                .planType(cs.getPlanType())
                .amount(cs.getAmount())
                .currencyCode(cs.getCurrencyCode())
                .startsAt(cs.getStartsAt())
                .endsAt(cs.getEndsAt())
                .status(cs.getStatus())
                .warningDays(cs.getWarningDays())
                .graceDays(cs.getGraceDays())
                .hrEntitled(cs.isHrEntitled())
                .financeEntitled(cs.isFinanceEntitled())
                .inventoryEntitled(cs.isInventoryEntitled())
                .notes(cs.getNotes())
                .daysRemaining(daysRemaining(cs))
                .locked(computeLocked(cs))
                .createdAt(cs.getCreatedAt())
                .updatedAt(cs.getUpdatedAt());

        lastPay.ifPresent(p -> {
            b.lastPaymentOn(p.getPaidOn());
            b.lastPaymentAmount(p.getAmount());
        });

        if (withHistory) {
            b.payments(paymentRepository.findByCompanySubscriptionIdOrderByPaidOnDescCreatedAtDesc(cs.getId())
                    .stream().map(this::toPaymentDto).collect(Collectors.toList()));
            b.reminders(reminderLogRepository.findByCompanySubscriptionIdOrderBySentAtDesc(cs.getId())
                    .stream().map(this::toReminderDto).collect(Collectors.toList()));
        }
        return b.build();
    }

    private CompanySubscriptionResponse toDetail(CompanySubscription cs) {
        return toListItem(cs, true);
    }

    private SubscriptionPaymentResponse toPaymentDto(SubscriptionPayment p) {
        return SubscriptionPaymentResponse.builder()
                .id(p.getId())
                .companySubscriptionId(p.getCompanySubscriptionId())
                .companyId(p.getCompanyId())
                .amount(p.getAmount())
                .paidOn(p.getPaidOn())
                .methodNote(p.getMethodNote())
                .periodStart(p.getPeriodStart())
                .periodEnd(p.getPeriodEnd())
                .recordedBy(p.getRecordedBy())
                .createdAt(p.getCreatedAt())
                .build();
    }

    private SubscriptionReminderLogResponse toReminderDto(SubscriptionReminderLog r) {
        return SubscriptionReminderLogResponse.builder()
                .id(r.getId())
                .reminderType(r.getReminderType())
                .periodKey(r.getPeriodKey())
                .sentAt(r.getSentAt())
                .toEmail(r.getToEmail())
                .success(r.isSuccess())
                .error(r.getError())
                .build();
    }

    private Integer daysRemaining(CompanySubscription cs) {
        if (cs.getEndsAt() == null) return null;
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), cs.getEndsAt());
    }

    private String billingEmail(Company company) {
        if (company == null) return null;
        if (company.getBillingEmail() != null && !company.getBillingEmail().isBlank()) {
            return company.getBillingEmail();
        }
        return company.getCompanyEmail();
    }

    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal p) {
            return p.getRole() == Role.SUPER_ADMIN;
        }
        String role = authContext.getCurrentUserRole();
        return Role.SUPER_ADMIN.name().equals(role);
    }

    private String currentActor() {
        Long id = authContext.getCurrentUserId();
        return id != null ? String.valueOf(id) : "system";
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }
}
