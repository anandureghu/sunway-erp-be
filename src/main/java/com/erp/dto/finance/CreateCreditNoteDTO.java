package com.erp.dto.finance;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateCreditNoteDTO {

    private String invoiceId;
    private BigDecimal amount;
    private String reason;
    private LocalDate creditDate;

    /**
     * Null/true (default) = today's behavior: apply immediately, reducing this invoice's
     * outstanding balance. False = create as a standing "AVAILABLE" credit for the invoice's
     * customer/supplier, not tied to this invoice's balance, for use on a future payment.
     */
    private Boolean applyImmediately;
}
