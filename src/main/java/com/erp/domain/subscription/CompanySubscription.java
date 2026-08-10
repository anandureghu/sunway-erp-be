package com.erp.domain.subscription;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "company_subscriptions")
public class CompanySubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private SubscriptionPlanType planType;

    @Builder.Default
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency_code", length = 10)
    private String currencyCode;

    @Column(name = "starts_at", nullable = false)
    private LocalDate startsAt;

    @Column(name = "ends_at")
    private LocalDate endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Builder.Default
    @Column(name = "warning_days", nullable = false)
    private int warningDays = 7;

    @Builder.Default
    @Column(name = "grace_days", nullable = false)
    private int graceDays = 0;

    @Builder.Default
    @Column(name = "hr_entitled", nullable = false)
    private boolean hrEntitled = true;

    @Builder.Default
    @Column(name = "finance_entitled", nullable = false)
    private boolean financeEntitled = true;

    @Builder.Default
    @Column(name = "inventory_entitled", nullable = false)
    private boolean inventoryEntitled = true;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
