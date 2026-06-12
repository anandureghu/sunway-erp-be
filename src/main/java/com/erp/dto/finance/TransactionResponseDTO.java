package com.erp.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class TransactionResponseDTO {
    private Long id;
    private String transactionCode;
    private String transactionType;
    //    private String fiscalType;
    private LocalDate transactionDate;
    //    private Instant postedDate;
//    private Boolean posted;
    private BigDecimal amount;

    //    private String debitAccount;
    private Long creditAccountId;
    private String creditAccountCode;
    private String creditAccountName;
    private Long debitAccountId;
    private String debitAccountCode;
    private String debitAccountName;

    private Long companyId;
    private String companyName;

    //    private String itemCode;
    private String invoiceId;
    private String paymentId;

    private String transactionDescription;

    private Long relatedId;
    private Long relatedSubId;

    private String source;
    private Boolean sourceLocked;

    private Boolean archived;
    private Instant archivedAt;
    private Long archivedByUserId;
    private String archivedByUserName;
    private Long createdByUserId;
    private String createdByUserName;
    private Instant createdAt;
}
