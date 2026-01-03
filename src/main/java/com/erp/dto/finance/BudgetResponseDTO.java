package com.erp.dto.finance;

import com.erp.domain.finance.BudgetStatus;
import lombok.*;

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
    private Integer budgetYear;
    private BudgetStatus status;
    private Long amount;
    private LocalDate startDate;
    private LocalDate endDate;
    private Instant createdAt;
    private Instant updatedAt;
    private Long companyId;
    private Long createdByUserId;
    private Long approvedByUserId;
    private List<BudgetLineDTO> lines;
}
