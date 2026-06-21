package com.erp.dto.finance.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceDepartmentBudgetSpendDTO {

    private Long departmentId;
    private String departmentName;
    private String departmentCode;
    private BigDecimal budgeted;
    private BigDecimal spent;
    private BigDecimal remaining;
    private BigDecimal utilizationPercent;
}
