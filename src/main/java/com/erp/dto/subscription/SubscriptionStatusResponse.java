package com.erp.dto.subscription;

import com.erp.domain.subscription.SubscriptionPlanType;
import com.erp.domain.subscription.SubscriptionStatus;
import lombok.*;

import java.time.LocalDate;

/**
 * Lightweight status for any authenticated user (banner / hard lock).
 * Amounts omitted for non–SUPER_ADMIN callers.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionStatusResponse {

    private Long companyId;
    private String companyName;
    private SubscriptionPlanType planType;
    private SubscriptionStatus status;
    private LocalDate startsAt;
    private LocalDate endsAt;
    private int warningDays;
    private Integer daysRemaining;
    private boolean locked;
    private boolean showWarningBanner;
    private String billingContactEmail;

    /** Present only for SUPER_ADMIN. */
    private java.math.BigDecimal amount;
    private String currencyCode;
}
