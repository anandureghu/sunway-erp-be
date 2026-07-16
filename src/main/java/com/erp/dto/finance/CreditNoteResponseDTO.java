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

    private String status; // DRAFT, APPLIED, AVAILABLE, PARTIALLY_APPLIED

    private String project; // optional
    private String referenceNumber; // invoice number

    private BigDecimal amount;
    private BigDecimal remainingAmount;
}
