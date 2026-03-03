package com.erp.dto.finance;

import com.erp.domain.finance.ReconciliationStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ReconciliationResponse {

    private Long id;

    private Long accountId;
    private String accountCode;
    private String accountName;

    private BigDecimal amount;
    private BigDecimal initialBalance;
    private BigDecimal newBalance;

    private String resource;
    private String reason;

    private ReconciliationStatus status;

    private Long createdById;
    private String createdByName;

    private Long updatedById;
    private String updatedByName;

    private Long confirmedById;
    private String confirmedByName;

    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
}