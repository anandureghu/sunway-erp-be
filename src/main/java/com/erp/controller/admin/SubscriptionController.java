package com.erp.controller.admin;

import com.erp.domain.subscription.SubscriptionPlanType;
import com.erp.domain.subscription.SubscriptionStatus;
import com.erp.dto.common.PagedResponse;
import com.erp.dto.subscription.*;
import com.erp.service.subscription.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/me/status")
    public SubscriptionStatusResponse myStatus() {
        return subscriptionService.getMyStatus();
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SubscriptionAnalyticsResponse analytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return subscriptionService.analytics(from, to);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PagedResponse<CompanySubscriptionResponse> list(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) SubscriptionPlanType planType,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Integer expiringWithinDays,
            @PageableDefault(size = 20, sort = "endsAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return PagedResponse.from(
                subscriptionService.list(status, planType, companyId, expiringWithinDays, pageable)
        );
    }

    @GetMapping("/{companyId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanySubscriptionResponse get(@PathVariable Long companyId) {
        return subscriptionService.getByCompanyId(companyId);
    }

    @PutMapping("/{companyId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanySubscriptionResponse assign(
            @PathVariable Long companyId,
            @Valid @RequestBody AssignSubscriptionRequest request
    ) {
        return subscriptionService.assign(companyId, request);
    }

    @PostMapping("/{companyId}/payments")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanySubscriptionResponse recordPayment(
            @PathVariable Long companyId,
            @Valid @RequestBody RecordSubscriptionPaymentRequest request
    ) {
        return subscriptionService.recordPayment(companyId, request);
    }

    @PostMapping("/{companyId}/extend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanySubscriptionResponse extend(
            @PathVariable Long companyId,
            @Valid @RequestBody ExtendSubscriptionRequest request
    ) {
        return subscriptionService.extend(companyId, request);
    }

    @PostMapping("/{companyId}/cancel")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanySubscriptionResponse cancel(
            @PathVariable Long companyId,
            @RequestBody(required = false) CancelSubscriptionRequest request
    ) {
        return subscriptionService.cancel(companyId, request != null ? request : new CancelSubscriptionRequest());
    }
}
