package com.erp.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class BudgetDistributionResponseDTO {
    private Long id;
    private String transactionCode;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private Long creditAccountId;
    private String creditAccountName;
    private String creditAccountCode;
    private Long debitAccountId;
    private String debitAccountName;
    private String transactionDescription;
    private Long createdByUserId;
    private String createdByUserName;
    private Instant createdAt;
    private Boolean archived;
    private Instant archivedAt;
    private Long archivedByUserId;
    private String archivedByUserName;
}
