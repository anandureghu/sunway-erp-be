package com.erp.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class InvoiceResponse {

    private Long id;
    private String invoiceId;

    private Long companyId;
    private String companyName;

    private String toParty;
    private String status;

    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private LocalDate paidDate;

    private BigDecimal amount;
    private BigDecimal openAmount;
    private BigDecimal outstanding;

    private String itemDescription;
    private String notesRemarks;

    private Integer gracePeriod;
    private BigDecimal interestRate;

    private String partyClassification;
    private String pdfUrl;

    private String type;
    private Long orderId;

    private Long creditAccountId;
    private Long debitAccountId;

    private String creditAccountName;
    private String debitAccountName;

    private Instant createdAt;
}
