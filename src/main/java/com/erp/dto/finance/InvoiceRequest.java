package com.erp.dto.finance;

import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
public class InvoiceRequest {
    private Long companyId;
    private String toParty;
    private Instant invoiceDate;
    private Instant dueDate;
    private BigDecimal amount;
    private String itemDescription;
    private String notesRemarks;
    private Integer gracePeriod;
    private BigDecimal interestRate;
    private String partyClassification;
}
