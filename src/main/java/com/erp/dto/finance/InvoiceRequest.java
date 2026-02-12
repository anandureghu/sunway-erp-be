package com.erp.dto.finance;

import com.erp.domain.InvoiceType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class InvoiceRequest {
    private String toParty;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private BigDecimal amount;
    private String itemDescription;
    private String notesRemarks;
    private Integer gracePeriod;
    private BigDecimal interestRate;
    private String partyClassification;
    private InvoiceType type;
    private Long orderId;
    private Long creditAccount;
    private Long debitAccount;
}
