package com.erp.dto.subscription;

import com.erp.domain.subscription.SubscriptionPlanType;
import com.erp.domain.subscription.SubscriptionStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanySubscriptionResponse {

    private Long id;
    private Long companyId;
    private String companyName;
    private SubscriptionPlanType planType;
    private BigDecimal amount;
    private String currencyCode;
    private LocalDate startsAt;
    private LocalDate endsAt;
    private SubscriptionStatus status;
    private int warningDays;
    private int graceDays;
    private boolean hrEntitled;
    private boolean financeEntitled;
    private boolean inventoryEntitled;
    private String notes;
    private Integer daysRemaining;
    private boolean locked;
    private LocalDate lastPaymentOn;
    private BigDecimal lastPaymentAmount;
    private Instant createdAt;
    private Instant updatedAt;
    private List<SubscriptionPaymentResponse> payments;
    private List<SubscriptionReminderLogResponse> reminders;
}
