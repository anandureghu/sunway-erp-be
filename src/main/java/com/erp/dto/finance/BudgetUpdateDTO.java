package com.erp.dto.finance;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetUpdateDTO {
    private String budgetName;
    private Integer budgetYear;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long amount;
}
