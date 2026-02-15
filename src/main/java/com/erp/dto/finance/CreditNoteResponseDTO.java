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

    private String status; // DRAFT, APPLIED, PARTIALLY_APPLIED

    private String project; // optional
    private String referenceNumber; // invoice number

    private BigDecimal amount;
    private BigDecimal remainingAmount;
}
