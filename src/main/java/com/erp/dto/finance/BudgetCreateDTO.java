package com.erp.dto.finance;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetCreateDTO {
    private String budgetName;
    private Integer budgetYear;
    private LocalDate startDate;
    private Long amount;
    private LocalDate endDate;
}
