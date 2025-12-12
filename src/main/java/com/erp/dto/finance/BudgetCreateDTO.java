package com.erp.dto.finance;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetCreateDTO {
    private String budgetName;
    private Integer budgetYear;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<BudgetLineDTO> lines;
}
