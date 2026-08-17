package com.erp.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class CreditNoteResponseDTO {

    private Long id;
    private String creditNoteNumber;
    private LocalDate creditNoteDate;

    private String customerName;
    private String supplierName;
    private Long customerId;
    private Long supplierId;

    private String status; // DRAFT, APPLIED, AVAILABLE, PARTIALLY_APPLIED, CASHED

    private String project; // optional
    private String referenceNumber; // invoice number
    private String source;
    private String reason;

    private BigDecimal amount;
    private BigDecimal remainingAmount;
    /** Payment code created when this note was cashed out (refund/redemption). */
    private String cashOutPaymentCode;
}
