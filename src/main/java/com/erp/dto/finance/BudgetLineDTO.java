package com.erp.dto.finance;

import com.erp.domain.finance.BudgetStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetLineDTO {
    private Long id;
    private Long accountId;
    private String accountName;
    private String accountCode;
    private Long departmentId;
    private String departmentName;
    private String departmentCode;
    private String projectId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal amount;
    private String notes;
    private BudgetStatus status;

    private Instant createdAt;
    private Instant updatedAt;
    private Long companyId;
    private Long createdByUserId;
    private String createdByUserName;
    private Long updatedByUserId;
    private String updatedByUserName;
    private Long approvedByUserId;
    private String approvedByUserName;

}

