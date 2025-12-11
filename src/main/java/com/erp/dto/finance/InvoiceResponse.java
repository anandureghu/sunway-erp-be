package com.erp.dto.finance;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class InvoiceResponse {

    private Long id;
    private String invoiceId;

    private Long companyId;
    private String companyName;

    private String toParty;
    private String status;

    private Instant invoiceDate;
    private Instant dueDate;
    private Instant paidDate;

    private BigDecimal amount;
    private BigDecimal openAmount;
    private BigDecimal outstanding;

    private String itemDescription;
    private String notesRemarks;

    private Integer gracePeriod;
    private BigDecimal interestRate;

    private String partyClassification;
    private String pdfUrl;

    private Instant createdAt;
}
