package com.erp.service.finance.report;

import com.erp.domain.InvoiceType;
import com.erp.domain.finance.COAType;
import com.erp.domain.finance.PaymentDirection;
import com.erp.dto.finance.report.FinanceAccountAmountDTO;
import com.erp.dto.finance.report.FinanceAgingBucketsDTO;
import com.erp.dto.finance.report.FinanceMonthlyPointDTO;
import com.erp.dto.finance.report.FinancePartyRowDTO;
import com.erp.dto.finance.report.FinanceReportSummaryDTO;
import com.erp.dto.finance.report.FinanceReportTotalsDTO;
import com.erp.repo.finance.InvoiceRepository;
import com.erp.repo.finance.PaymentRepository;
import com.erp.repo.finance.TransactionRepository;
import com.erp.repo.salary.PayrollRepository;
import com.erp.security.context.AuthContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class FinanceReportService {

    private static final int TOP_PARTIES = 10;
    private static final int TOP_ACCOUNTS = 10;

    private final InvoiceRepository invoiceRepo;
    private final PaymentRepository paymentRepo;
    private final TransactionRepository transactionRepo;
    private final PayrollRepository payrollRepo;
    private final AuthContext auth;

    public FinanceReportService(
            InvoiceRepository invoiceRepo,
            PaymentRepository paymentRepo,
            TransactionRepository transactionRepo,
            PayrollRepository payrollRepo,
            AuthContext auth
    ) {
        this.invoiceRepo = invoiceRepo;
        this.paymentRepo = paymentRepo;
        this.transactionRepo = transactionRepo;
        this.payrollRepo = payrollRepo;
        this.auth = auth;
    }

    public FinanceReportSummaryDTO buildSummary(LocalDate from, LocalDate to) {
        Long companyId = auth.getCurrentCompanyId();
        if (companyId == null) {
            throw new RuntimeException("User is not associated with a company");
        }

        LocalDate today = LocalDate.now();
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom = from != null
                ? from
                : effectiveTo.minusMonths(11).withDayOfMonth(1);

        // Totals
        BigDecimal revenue = nz(invoiceRepo.sumByTypeBetween(
                companyId, InvoiceType.SALES, effectiveFrom, effectiveTo));
        BigDecimal purchases = nz(invoiceRepo.sumByTypeBetween(
                companyId, InvoiceType.PURCHASE, effectiveFrom, effectiveTo));

        Object[] arOutstandingRow = invoiceRepo.outstandingByType(companyId, InvoiceType.SALES);
        Object[] apOutstandingRow = invoiceRepo.outstandingByType(companyId, InvoiceType.PURCHASE);
        BigDecimal totalReceivables = toBigDecimal(valueAt(arOutstandingRow, 0));
        BigDecimal totalPayables = toBigDecimal(valueAt(apOutstandingRow, 0));

        Object[] inflowRow = paymentRepo.sumByDirectionBetween(
                companyId, PaymentDirection.CUSTOMER, effectiveFrom, effectiveTo);
        Object[] outflowRow = paymentRepo.sumByDirectionBetween(
                companyId, PaymentDirection.VENDOR, effectiveFrom, effectiveTo);
        BigDecimal cashInflow = toBigDecimal(valueAt(inflowRow, 0));
        BigDecimal cashOutflow = toBigDecimal(valueAt(outflowRow, 0));
        long inflowCount = toLong(valueAt(inflowRow, 1));
        long outflowCount = toLong(valueAt(outflowRow, 1));

        Double payrollGross = payrollRepo.sumPayrollCostBetween(
                companyId, effectiveFrom, effectiveTo);
        BigDecimal payrollCost = BigDecimal.valueOf(payrollGross == null ? 0.0 : payrollGross);

        // Expenses for the netProfit number: prefer ledger-based expense from transactions
        // (more precise than purchase invoice totals which include unpaid + tax).
        List<Object[]> expenseAccountsRaw = transactionRepo.aggregateByDebitAccountTypes(
                companyId,
                List.of(COAType.EXPENSE, COAType.COST),
                effectiveFrom,
                effectiveTo,
                PageRequest.of(0, TOP_ACCOUNTS));
        BigDecimal expenseTotalFromLedger = sumThird(expenseAccountsRaw);
        // If no GL data exists yet, fall back to purchase invoices as expense proxy.
        BigDecimal expenses = expenseTotalFromLedger.signum() > 0
                ? expenseTotalFromLedger
                : purchases;

        BigDecimal netProfit = revenue.subtract(expenses);

        long invoiceCount = invoiceRepo.countInvoicesBetween(companyId, effectiveFrom, effectiveTo);

        FinanceReportTotalsDTO totals = FinanceReportTotalsDTO.builder()
                .revenue(revenue)
                .expenses(expenses)
                .netProfit(netProfit)
                .totalReceivables(totalReceivables)
                .totalPayables(totalPayables)
                .cashInflow(cashInflow)
                .cashOutflow(cashOutflow)
                .payrollCost(payrollCost)
                .invoiceCount(invoiceCount)
                .paymentCount(inflowCount + outflowCount)
                .build();

        // Monthly series (filled with zeros for missing months across the window)
        List<String> months = monthLabels(effectiveFrom, effectiveTo);

        List<FinanceMonthlyPointDTO> revenueByMonth = monthlySeries(
                invoiceRepo.monthlyByType(companyId, InvoiceType.SALES, effectiveFrom, effectiveTo),
                months);
        List<FinanceMonthlyPointDTO> expenseByMonth = monthlySeries(
                invoiceRepo.monthlyByType(companyId, InvoiceType.PURCHASE, effectiveFrom, effectiveTo),
                months);
        List<FinanceMonthlyPointDTO> cashInflowByMonth = monthlySeries(
                paymentRepo.monthlyByDirection(companyId, PaymentDirection.CUSTOMER, effectiveFrom, effectiveTo),
                months);
        List<FinanceMonthlyPointDTO> cashOutflowByMonth = monthlySeries(
                paymentRepo.monthlyByDirection(companyId, PaymentDirection.VENDOR, effectiveFrom, effectiveTo),
                months);

        // Aging buckets (computed in Java for portability across DB dialects)
        FinanceAgingBucketsDTO arAging = computeAging(
                invoiceRepo.openInvoicesForAging(companyId, InvoiceType.SALES), today);
        FinanceAgingBucketsDTO apAging = computeAging(
                invoiceRepo.openInvoicesForAging(companyId, InvoiceType.PURCHASE), today);

        // Top customers / vendors
        List<FinancePartyRowDTO> topCustomers = mapPartyRows(invoiceRepo.topPartiesByType(
                companyId, InvoiceType.SALES, effectiveFrom, effectiveTo,
                PageRequest.of(0, TOP_PARTIES)));
        List<FinancePartyRowDTO> topVendors = mapPartyRows(invoiceRepo.topPartiesByType(
                companyId, InvoiceType.PURCHASE, effectiveFrom, effectiveTo,
                PageRequest.of(0, TOP_PARTIES)));

        // Income / Expenses by account
        List<FinanceAccountAmountDTO> incomeByAccount = mapAccountRows(
                transactionRepo.aggregateByCreditAccountTypes(
                        companyId,
                        List.of(COAType.REVENUE, COAType.INCOME),
                        effectiveFrom, effectiveTo,
                        PageRequest.of(0, TOP_ACCOUNTS)));
        List<FinanceAccountAmountDTO> expensesByAccount = mapAccountRows(expenseAccountsRaw);

        return FinanceReportSummaryDTO.builder()
                .from(effectiveFrom)
                .to(effectiveTo)
                .totals(totals)
                .revenueByMonth(revenueByMonth)
                .expenseByMonth(expenseByMonth)
                .cashInflowByMonth(cashInflowByMonth)
                .cashOutflowByMonth(cashOutflowByMonth)
                .arAging(arAging)
                .apAging(apAging)
                .topCustomers(topCustomers)
                .topVendors(topVendors)
                .incomeByAccount(incomeByAccount)
                .expensesByAccount(expensesByAccount)
                .generatedAt(Instant.now())
                .build();
    }

    // ======================================================
    //  Helpers
    // ======================================================

    private static List<String> monthLabels(LocalDate from, LocalDate to) {
        List<String> labels = new ArrayList<>();
        LocalDate cursor = from.withDayOfMonth(1);
        LocalDate end = to.withDayOfMonth(1);
        while (!cursor.isAfter(end)) {
            labels.add(formatYearMonth(cursor.getYear(), cursor.getMonthValue()));
            cursor = cursor.plusMonths(1);
        }
        return labels;
    }

    private static String formatYearMonth(int year, int month) {
        return String.format("%04d-%02d", year, month);
    }

    private static List<FinanceMonthlyPointDTO> monthlySeries(
            List<Object[]> rows, List<String> months) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            BigDecimal amount = toBigDecimal(row[2]);
            map.put(formatYearMonth(year, month), amount);
        }
        List<FinanceMonthlyPointDTO> out = new ArrayList<>(months.size());
        for (String label : months) {
            out.add(FinanceMonthlyPointDTO.builder()
                    .yearMonth(label)
                    .value(map.getOrDefault(label, BigDecimal.ZERO))
                    .build());
        }
        return out;
    }

    private static FinanceAgingBucketsDTO computeAging(List<Object[]> rows, LocalDate asOf) {
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal d1To30 = BigDecimal.ZERO;
        BigDecimal d31To60 = BigDecimal.ZERO;
        BigDecimal d61To90 = BigDecimal.ZERO;
        BigDecimal d90Plus = BigDecimal.ZERO;
        long currentCount = 0;
        long d1To30Count = 0;
        long d31To60Count = 0;
        long d61To90Count = 0;
        long d90PlusCount = 0;

        for (Object[] row : rows) {
            LocalDate dueDate = (LocalDate) row[0];
            BigDecimal outstanding = toBigDecimal(row[1]);
            long daysOverdue = dueDate == null
                    ? 0L
                    : ChronoUnit.DAYS.between(dueDate, asOf);

            if (dueDate == null || daysOverdue <= 0) {
                current = current.add(outstanding);
                currentCount++;
            } else if (daysOverdue <= 30) {
                d1To30 = d1To30.add(outstanding);
                d1To30Count++;
            } else if (daysOverdue <= 60) {
                d31To60 = d31To60.add(outstanding);
                d31To60Count++;
            } else if (daysOverdue <= 90) {
                d61To90 = d61To90.add(outstanding);
                d61To90Count++;
            } else {
                d90Plus = d90Plus.add(outstanding);
                d90PlusCount++;
            }
        }

        return FinanceAgingBucketsDTO.builder()
                .current(current)
                .d1To30(d1To30)
                .d31To60(d31To60)
                .d61To90(d61To90)
                .d90Plus(d90Plus)
                .currentCount(currentCount)
                .d1To30Count(d1To30Count)
                .d31To60Count(d31To60Count)
                .d61To90Count(d61To90Count)
                .d90PlusCount(d90PlusCount)
                .build();
    }

    private static List<FinancePartyRowDTO> mapPartyRows(List<Object[]> rows) {
        List<FinancePartyRowDTO> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            out.add(FinancePartyRowDTO.builder()
                    .name((String) row[0])
                    .totalAmount(toBigDecimal(row[1]))
                    .outstanding(toBigDecimal(row[2]))
                    .invoiceCount(toLong(row[3]))
                    .build());
        }
        return out;
    }

    private static List<FinanceAccountAmountDTO> mapAccountRows(List<Object[]> rows) {
        List<FinanceAccountAmountDTO> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            out.add(FinanceAccountAmountDTO.builder()
                    .accountName((String) row[0])
                    .accountCode((String) row[1])
                    .amount(toBigDecimal(row[2]))
                    .build());
        }
        return out;
    }

    private static BigDecimal sumThird(List<Object[]> rows) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Object[] row : rows) {
            sum = sum.add(toBigDecimal(row[2]));
        }
        return sum;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static Object valueAt(Object[] row, int index) {
        if (row == null || index < 0 || index >= row.length) return null;
        return row[index];
    }
}
