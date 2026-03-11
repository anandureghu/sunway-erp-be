package com.erp.dto.finance;

import com.erp.domain.finance.BudgetStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetResponseDTO {
    private Long id;
    private String budgetName;
    private String projectId;
    private String fiscalYear;
    private BudgetStatus status;
    private BigDecimal amount;
    private LocalDate startDate;
    private LocalDate endDate;
    private Instant createdAt;
    private Instant updatedAt;
    private Long companyId;
    private Long createdByUserId;
    private String createdByUserName;
    private Long updatedByUserId;
    private String updatedByUserName;
    private Long approvedByUserId;
    private String approvedByUserName;
    private List<BudgetLineDTO> lines;
    private boolean isActive;
}
