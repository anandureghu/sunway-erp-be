package com.erp.dto.dashboard.finance;

import com.erp.dto.finance.report.FinanceAgingBucketsDTO;
import com.erp.dto.finance.report.FinanceDepartmentBudgetSpendDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinanceDashboardResponseDTO {

    private FinanceDashboardKpisDTO kpis;

    private List<FinanceTrendPointDTO> revenueExpenseTrend;

    private FinanceAgingBucketsDTO receivablesAging;
    private FinanceAgingBucketsDTO payablesAging;

    private List<FinanceInvoiceRowDTO> topOverdueReceivables;
    private List<FinanceInvoiceRowDTO> topPayablesDue;

    private List<FinanceDepartmentBudgetSpendDTO> budgetUtilizationByDepartment;

    private FinancePendingApprovalsDTO pendingApprovals;

    private List<FinanceTransactionRowDTO> recentFinancialTransactions;

    private FinancePaymentStatusDTO paymentStatus;

    private List<FinanceCriticalAlertDTO> criticalAlerts;

    private Instant generatedAt;
}
