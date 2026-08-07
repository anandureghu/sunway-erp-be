package com.erp.dto.subscription;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class RecordSubscriptionPaymentRequest {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal amount;

    @NotNull
    private LocalDate paidOn;

    private String methodNote;

    private LocalDate periodStart;

    /** New subscription end date after this payment. If null, server extends by plan period. */
    private LocalDate periodEnd;

    private String idempotencyKey;

    /** When true (default), reactivate subscription and set endsAt from periodEnd / auto-extend. */
    private Boolean extendSubscription;
}
