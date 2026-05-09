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
public class FinanceReportTotalsDTO {

    private BigDecimal revenue;
    private BigDecimal expenses;
    private BigDecimal netProfit;
    private BigDecimal totalReceivables;
    private BigDecimal totalPayables;
    private BigDecimal cashInflow;
    private BigDecimal cashOutflow;
    private BigDecimal payrollCost;

    private long invoiceCount;
    private long paymentCount;
}
