package com.erp.dto.finance;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetLineCreateDTO {
    private Long accountId;
    private Long departmentId;
    private String projectId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long amount;
    private String notes;
}

