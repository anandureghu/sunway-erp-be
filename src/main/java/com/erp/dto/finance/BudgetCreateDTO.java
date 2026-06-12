package com.erp.dto.finance;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetCreateDTO {
    private String budgetName;
    private String fiscalYear;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal amount;
    private String budgetType;
    private Long budgetAccountId;
    private String projectId;
}
