package com.erp.dto.subscription;

import com.erp.domain.subscription.SubscriptionPlanType;
import com.erp.domain.subscription.SubscriptionStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class AssignSubscriptionRequest {

    @NotNull
    private SubscriptionPlanType planType;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal amount;

    private String currencyCode;

    @NotNull
    private LocalDate startsAt;

    /** Required for MONTHLY/YEARLY/CUSTOM unless auto-calculated server-side. Null allowed for FREE. */
    private LocalDate endsAt;

    @Min(0)
    private Integer warningDays;

    @Min(0)
    private Integer graceDays;

    private Boolean hrEntitled;
    private Boolean financeEntitled;
    private Boolean inventoryEntitled;

    /** Max total storage (cloud + database) in bytes. When null, uses plan default (or keeps existing on edit). */
    @Min(0)
    private Long maxStorageBytes;

    private String notes;

    /** When true, sync company module flags from entitled modules. Default true. */
    private Boolean syncCompanyModules;
}
