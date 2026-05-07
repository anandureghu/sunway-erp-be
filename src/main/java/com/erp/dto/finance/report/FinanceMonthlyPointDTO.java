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
public class FinanceMonthlyPointDTO {

    /** ISO month tag like "2025-01". */
    private String yearMonth;

    private BigDecimal value;
}
