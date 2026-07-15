package com.erp.dto.dashboard.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceDashboardKpisDTO {

    private BigDecimal revenueThisMonth;
    private BigDecimal expensesThisMonth;
    private BigDecimal netProfitThisMonth;
    private BigDecimal receivablesOutstanding;
    private BigDecimal payablesOutstanding;
    private BigDecimal cashBalance;
    private BigDecimal budgetUtilizationPercent;
    private long pendingApprovalsCount;
}
