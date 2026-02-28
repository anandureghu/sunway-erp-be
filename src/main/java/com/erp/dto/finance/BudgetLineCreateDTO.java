package com.erp.dto.finance;

import lombok.*;

import java.math.BigDecimal;
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
    private BigDecimal amount;
    private String notes;
}

