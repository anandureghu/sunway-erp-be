package com.erp.dto.finance.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceReportSummaryDTO {

    private LocalDate from;
    private LocalDate to;

    private FinanceReportTotalsDTO totals;

    private List<FinanceMonthlyPointDTO> revenueByMonth;
    private List<FinanceMonthlyPointDTO> expenseByMonth;
    private List<FinanceMonthlyPointDTO> cashInflowByMonth;
    private List<FinanceMonthlyPointDTO> cashOutflowByMonth;

    private FinanceAgingBucketsDTO arAging;
    private FinanceAgingBucketsDTO apAging;

    private List<FinancePartyRowDTO> topCustomers;
    private List<FinancePartyRowDTO> topVendors;

    private List<FinanceAccountAmountDTO> incomeByAccount;
    private List<FinanceAccountAmountDTO> expensesByAccount;

    private Instant generatedAt;
}
