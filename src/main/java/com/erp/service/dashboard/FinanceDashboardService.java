package com.erp.service.dashboard;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.COAType;
import com.erp.domain.finance.Invoice;
import com.erp.domain.finance.JournalEntryStatus;
import com.erp.domain.finance.Transaction;
import com.erp.domain.purchase.PurchaseOrderStatus;
import com.erp.domain.purchase.PurchaseRequisitionStatus;
import com.erp.dto.dashboard.finance.FinanceCriticalAlertDTO;
import com.erp.dto.dashboard.finance.FinanceDashboardKpisDTO;
import com.erp.dto.dashboard.finance.FinanceDashboardResponseDTO;
import com.erp.dto.dashboard.finance.FinanceInvoiceRowDTO;
import com.erp.dto.dashboard.finance.FinancePaymentStatusDTO;
import com.erp.dto.dashboard.finance.FinancePendingApprovalsDTO;
import com.erp.dto.dashboard.finance.FinanceTransactionRowDTO;
import com.erp.dto.dashboard.finance.FinanceTrendPointDTO;
import com.erp.dto.finance.report.FinanceAgingBucketsDTO;
import com.erp.dto.finance.report.FinanceDepartmentBudgetSpendDTO;
import com.erp.dto.finance.report.FinanceMonthlyPointDTO;
import com.erp.dto.finance.report.FinanceReportSummaryDTO;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.finance.JournalEntryRepository;
import com.erp.repo.finance.PaymentRepository;
import com.erp.repo.finance.TransactionRepository;
import com.erp.repo.purchase.PurchaseOrderRepository;
import com.erp.repo.purchase.PurchaseRequisitionRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.finance.report.FinanceReportService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates existing finance data into the shape the Finance Manager dashboard
 * widgets need. Reuses {@link FinanceReportService} for KPIs/trend/aging/budget
 * rather than re-deriving that logic.
 */
@Service
@Transactional(readOnly = true)
public class FinanceDashboardService {

    private static final int TOP_ROWS = 5;
    private static final int RECENT_TRANSACTIONS = 10;
    private static final int TREND_MONTHS = 6;

    private final FinanceReportService financeReportService;
    private final InvoiceRepository invoiceRepo;
    private final TransactionRepository transactionRepo;
    private final ChartOfAccountsRepository chartOfAccountsRepo;
    private final PurchaseRequisitionRepository purchaseRequisitionRepo;
    private final PurchaseOrderRepository purchaseOrderRepo;
    private final PaymentRepository paymentRepo;
    private final JournalEntryRepository journalEntryRepo;
    private final AuthContext auth;

    public FinanceDashboardService(
            FinanceReportService financeReportService,
            InvoiceRepository invoiceRepo,
            TransactionRepository transactionRepo,
            ChartOfAccountsRepository chartOfAccountsRepo,
            PurchaseRequisitionRepository purchaseRequisitionRepo,
            PurchaseOrderRepository purchaseOrderRepo,
            PaymentRepository paymentRepo,
            JournalEntryRepository journalEntryRepo,
            AuthContext auth
    ) {
        this.financeReportService = financeReportService;
        this.invoiceRepo = invoiceRepo;
        this.transactionRepo = transactionRepo;
        this.chartOfAccountsRepo = chartOfAccountsRepo;
        this.purchaseRequisitionRepo = purchaseRequisitionRepo;
        this.purchaseOrderRepo = purchaseOrderRepo;
        this.paymentRepo = paymentRepo;
        this.journalEntryRepo = journalEntryRepo;
        this.auth = auth;
    }

    public FinanceDashboardResponseDTO build() {
        Long companyId = auth.getCurrentCompanyId();
        if (companyId == null) {
            throw new RuntimeException("User is not associated with a company");
        }

        LocalDate today = LocalDate.now();
        LocalDate trendFrom = today.minusMonths(TREND_MONTHS - 1L).withDayOfMonth(1);
        LocalDate startOfMonth = today.withDayOfMonth(1);

        FinanceReportSummaryDTO summary = financeReportService.buildSummary(trendFrom, today);

        List<FinanceTrendPointDTO> trend = mergeTrend(summary.getRevenueByMonth(), summary.getExpenseByMonth());
        BigDecimal revenueThisMonth = lastValue(trend, true);
        BigDecimal expensesThisMonth = lastValue(trend, false);
        BigDecimal netProfitThisMonth = revenueThisMonth.subtract(expensesThisMonth);

        BigDecimal cashBalance = nz(chartOfAccountsRepo.sumBalanceByCompanyAndType(companyId, COAType.CASH));

        List<FinanceDepartmentBudgetSpendDTO> departmentBudgetSpend = summary.getDepartmentBudgetSpend();
        BigDecimal budgetUtilizationPercent = overallUtilization(departmentBudgetSpend);

        long pendingPurchaseOrders = purchaseOrderRepo.countByCompanyIdAndArchivedFalseAndStatusIn(
                companyId, List.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.APPROVED));

        FinancePendingApprovalsDTO pendingApprovals = FinancePendingApprovalsDTO.builder()
                .purchaseRequisitions(purchaseRequisitionRepo.countByCompanyIdAndArchivedFalseAndStatus(
                        companyId, PurchaseRequisitionStatus.SUBMITTED))
                .purchaseOrders(pendingPurchaseOrders)
                .paymentRequests(paymentRepo.countPendingVendorPaymentsForCompany(companyId))
                .journalEntries(journalEntryRepo.countByCompanyIdAndArchivedFalseAndStatus(
                        companyId, JournalEntryStatus.PENDING_APPROVAL))
                .build();

        long pendingApprovalsCount = pendingApprovals.getPurchaseRequisitions()
                + pendingApprovals.getPurchaseOrders()
                + pendingApprovals.getPaymentRequests()
                + pendingApprovals.getJournalEntries();

        FinanceDashboardKpisDTO kpis = FinanceDashboardKpisDTO.builder()
                .revenueThisMonth(revenueThisMonth)
                .expensesThisMonth(expensesThisMonth)
                .netProfitThisMonth(netProfitThisMonth)
                .receivablesOutstanding(nz(summary.getTotals().getTotalReceivables()))
                .payablesOutstanding(nz(summary.getTotals().getTotalPayables()))
                .cashBalance(cashBalance)
                .budgetUtilizationPercent(budgetUtilizationPercent)
                .pendingApprovalsCount(pendingApprovalsCount)
                .build();

        List<FinanceInvoiceRowDTO> topOverdueReceivables = mapInvoiceRows(
                invoiceRepo.findOverdueInvoicesByTypeOrderByDueDateAsc(
                        companyId, InvoiceType.SALES, today, PageRequest.of(0, TOP_ROWS)),
                today);
        List<FinanceInvoiceRowDTO> topPayablesDue = mapInvoiceRows(
                invoiceRepo.findOpenInvoicesByTypeOrderByDueDateAsc(
                        companyId, InvoiceType.PURCHASE, PageRequest.of(0, TOP_ROWS)),
                today);

        List<FinanceTransactionRowDTO> recentTransactions = mapTransactionRows(
                transactionRepo.findByCompanyIdOrderByCreatedAtDesc(companyId));

        FinancePaymentStatusDTO paymentStatus = buildPaymentStatus(companyId, startOfMonth, today);

        List<FinanceCriticalAlertDTO> criticalAlerts = buildCriticalAlerts(
                summary.getArAging(), summary.getApAging(), departmentBudgetSpend);

        return FinanceDashboardResponseDTO.builder()
                .kpis(kpis)
                .revenueExpenseTrend(trend)
                .receivablesAging(summary.getArAging())
                .payablesAging(summary.getApAging())
                .topOverdueReceivables(topOverdueReceivables)
                .topPayablesDue(topPayablesDue)
                .budgetUtilizationByDepartment(departmentBudgetSpend)
                .pendingApprovals(pendingApprovals)
                .recentFinancialTransactions(recentTransactions)
                .paymentStatus(paymentStatus)
                .criticalAlerts(criticalAlerts)
                .generatedAt(Instant.now())
                .build();
    }

    private static List<FinanceTrendPointDTO> mergeTrend(
            List<FinanceMonthlyPointDTO> revenueByMonth, List<FinanceMonthlyPointDTO> expenseByMonth) {
        List<FinanceTrendPointDTO> out = new ArrayList<>(revenueByMonth.size());
        for (int i = 0; i < revenueByMonth.size(); i++) {
            FinanceMonthlyPointDTO rev = revenueByMonth.get(i);
            BigDecimal exp = i < expenseByMonth.size() ? expenseByMonth.get(i).getValue() : BigDecimal.ZERO;
            out.add(FinanceTrendPointDTO.builder()
                    .yearMonth(rev.getYearMonth())
                    .revenue(rev.getValue())
                    .expense(exp)
                    .build());
        }
        return out;
    }

    private static BigDecimal lastValue(List<FinanceTrendPointDTO> trend, boolean revenue) {
        if (trend.isEmpty()) return BigDecimal.ZERO;
        FinanceTrendPointDTO last = trend.get(trend.size() - 1);
        return nz(revenue ? last.getRevenue() : last.getExpense());
    }

    private static BigDecimal overallUtilization(List<FinanceDepartmentBudgetSpendDTO> rows) {
        BigDecimal budgeted = BigDecimal.ZERO;
        BigDecimal spent = BigDecimal.ZERO;
        for (FinanceDepartmentBudgetSpendDTO row : rows) {
            budgeted = budgeted.add(nz(row.getBudgeted()));
            spent = spent.add(nz(row.getSpent()));
        }
        if (budgeted.signum() <= 0) return BigDecimal.ZERO;
        return spent.multiply(BigDecimal.valueOf(100)).divide(budgeted, 2, RoundingMode.HALF_UP);
    }

    private static List<FinanceInvoiceRowDTO> mapInvoiceRows(List<Invoice> invoices, LocalDate today) {
        List<FinanceInvoiceRowDTO> out = new ArrayList<>(invoices.size());
        for (Invoice invoice : invoices) {
            long daysOverdue = invoice.getDueDate() == null
                    ? 0L
                    : ChronoUnit.DAYS.between(invoice.getDueDate(), today);
            out.add(FinanceInvoiceRowDTO.builder()
                    .invoiceId(invoice.getInvoiceId())
                    .party(invoice.getToParty())
                    .dueDate(invoice.getDueDate())
                    .daysOverdue(daysOverdue)
                    .amount(nz(invoice.getAmount()))
                    .outstanding(nz(invoice.getOutstanding()))
                    .build());
        }
        return out;
    }

    private static List<FinanceTransactionRowDTO> mapTransactionRows(List<Transaction> transactions) {
        List<FinanceTransactionRowDTO> out = new ArrayList<>();
        for (int i = 0; i < transactions.size() && i < RECENT_TRANSACTIONS; i++) {
            Transaction t = transactions.get(i);
            out.add(FinanceTransactionRowDTO.builder()
                    .transactionCode(t.getTransactionCode())
                    .transactionType(t.getTransactionType())
                    .description(t.getTransactionDescription())
                    .transactionDate(t.getTransactionDate())
                    .amount(nz(t.getAmount()))
                    .build());
        }
        return out;
    }

    private FinancePaymentStatusDTO buildPaymentStatus(Long companyId, LocalDate from, LocalDate to) {
        List<Invoice> invoices = new ArrayList<>();
        invoices.addAll(invoiceRepo.findByCompany_IdAndTypeAndArchivedFalseAndInvoiceDateBetween(
                companyId, InvoiceType.SALES, from, to));
        invoices.addAll(invoiceRepo.findByCompany_IdAndTypeAndArchivedFalseAndInvoiceDateBetween(
                companyId, InvoiceType.PURCHASE, from, to));

        long paid = 0, partiallyPaid = 0, unpaid = 0;
        for (Invoice invoice : invoices) {
            BigDecimal outstanding = nz(invoice.getOutstanding());
            BigDecimal amount = nz(invoice.getAmount());
            if (outstanding.signum() <= 0) {
                paid++;
            } else if (outstanding.compareTo(amount) >= 0) {
                unpaid++;
            } else {
                partiallyPaid++;
            }
        }

        return FinancePaymentStatusDTO.builder()
                .paidCount(paid)
                .partiallyPaidCount(partiallyPaid)
                .unpaidCount(unpaid)
                .totalCount(invoices.size())
                .build();
    }

    private static List<FinanceCriticalAlertDTO> buildCriticalAlerts(
            FinanceAgingBucketsDTO arAging,
            FinanceAgingBucketsDTO apAging,
            List<FinanceDepartmentBudgetSpendDTO> departmentBudgetSpend) {
        List<FinanceCriticalAlertDTO> alerts = new ArrayList<>();

        long overdueReceivablesCount = totalCount(arAging) - arAging.getCurrentCount();
        BigDecimal overdueReceivablesAmount = totalAmount(arAging).subtract(nz(arAging.getCurrent()));
        if (overdueReceivablesCount > 0) {
            alerts.add(FinanceCriticalAlertDTO.builder()
                    .type("OVERDUE_RECEIVABLES")
                    .message("Overdue receivables")
                    .count(overdueReceivablesCount)
                    .amount(overdueReceivablesAmount)
                    .build());
        }

        long overduePayablesCount = totalCount(apAging) - apAging.getCurrentCount();
        BigDecimal overduePayablesAmount = totalAmount(apAging).subtract(nz(apAging.getCurrent()));
        if (overduePayablesCount > 0) {
            alerts.add(FinanceCriticalAlertDTO.builder()
                    .type("OVERDUE_PAYABLES")
                    .message("Payables past due")
                    .count(overduePayablesCount)
                    .amount(overduePayablesAmount)
                    .build());
        }

        if (apAging.getD90PlusCount() > 0) {
            alerts.add(FinanceCriticalAlertDTO.builder()
                    .type("VENDOR_AGING")
                    .message("Vendor invoices over 90 days past due")
                    .count(apAging.getD90PlusCount())
                    .amount(nz(apAging.getD90Plus()))
                    .build());
        }

        long overBudgetDepartments = departmentBudgetSpend.stream()
                .filter(d -> nz(d.getUtilizationPercent()).compareTo(BigDecimal.valueOf(100)) > 0)
                .count();
        if (overBudgetDepartments > 0) {
            alerts.add(FinanceCriticalAlertDTO.builder()
                    .type("BUDGET_EXCEEDED")
                    .message("Departments over budget")
                    .count(overBudgetDepartments)
                    .amount(null)
                    .build());
        }

        return alerts;
    }

    private static long totalCount(FinanceAgingBucketsDTO aging) {
        return aging.getCurrentCount() + aging.getD1To30Count() + aging.getD31To60Count()
                + aging.getD61To90Count() + aging.getD90PlusCount();
    }

    private static BigDecimal totalAmount(FinanceAgingBucketsDTO aging) {
        return nz(aging.getCurrent()).add(nz(aging.getD1To30())).add(nz(aging.getD31To60()))
                .add(nz(aging.getD61To90())).add(nz(aging.getD90Plus()));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
