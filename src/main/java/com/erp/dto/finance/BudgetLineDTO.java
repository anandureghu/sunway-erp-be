package com.erp.dto.finance;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetLineDTO {
    private Long id;
    private Long accountId;
    private Long departmentId;
    private Long projectId;
    private Integer period;
    private BigDecimal amount;
    private String notes;
}

