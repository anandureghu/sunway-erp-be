package com.erp.dto.finance;

import com.erp.domain.finance.JournalEntryStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class JournalEntryResponse {

    private Long id;
    private String jeNumber;

    private Long creditAccountId;
    private String creditAccountName;
    private String creditAccountCode;

    private Long debitAccountId;
    private String debitAccountName;
    private String debitAccountCode;

    private BigDecimal amount;
    private String source;
    private String description;

    private JournalEntryStatus status;

    private Long createdById;
    private String createdByName;

    private Long updatedById;
    private String updatedByName;

    private Long approvedById;
    private String approvedByName;

    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    private Boolean archived;
    private LocalDateTime archivedAt;
    private Long archivedById;
    private String archivedByName;
}