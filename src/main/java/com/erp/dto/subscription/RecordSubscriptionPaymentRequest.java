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

    /** Invoice this payment settles. When omitted, links to current period invoice if available. */
    private Long invoiceId;

    /** When false, do not auto-link to the current period invoice. */
    private Boolean linkInvoice;

    private String idempotencyKey;

    /** When true (default), reactivate subscription and set endsAt from periodEnd / auto-extend. */
    private Boolean extendSubscription;

    /** Generate and optionally email a PDF receipt after recording payment. */
    private Boolean sendReceipt;
}
