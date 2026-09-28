package com.erp.service.finance.report;

import com.erp.domain.finance.COAType;
import com.erp.domain.finance.ChartOfAccounts;
import com.erp.domain.finance.Transaction;
import com.erp.dto.finance.report.FinanceAgingBucketsDTO;
import com.erp.dto.finance.report.FinanceReportSummaryDTO;
import com.erp.dto.finance.report.sheet.AccountSummaryReportDTO;
import com.erp.dto.finance.report.sheet.FinancialSummaryReportDTO;
import com.erp.exception.NotFoundException;
import com.erp.repo.finance.ChartOfAccountsRepository;
import com.erp.repo.finance.TransactionRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * FIN_SHEET_REPORTS — live data for Financial Summary and Account Summary sheets.
 * <p>
 * Added for the HR-style finance report pages (sidebar: Financial Summary / Account Summary).
 * When those pages are removed, delete this service, DTOs under {@code dto.finance.report.sheet},
 * controller mappings, repository helpers annotated FIN_SHEET_REPORTS, and the frontend
 * financial/account summary modules + routes + sidebar entries.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinanceSheetReportService {

    private static final Set<COAType> REVENUE_TYPES = EnumSet.of(COAType.REVENUE, COAType.INCOME);
    private static final Set<COAType> EXPENSE_TYPES = EnumSet.of(COAType.EXPENSE, COAType.COST);
    private static final Set<COAType> ASSET_TYPES = EnumSet.of(COAType.ASSET, COAType.CASH);
    private static final Set<COAType> LIABILITY_TYPES = EnumSet.of(COAType.LIABILITY, COAType.TAX);
    private static final Set<COAType> EQUITY_TYPES = EnumSet.of(COAType.EQUITY);

    private final ChartOfAccountsRepository coaRepo;
    private final TransactionRepository transactionRepo;
    private final FinanceReportService financeReportService;
    private final AuthContext auth;

    public FinancialSummaryReportDTO financialSummary(LocalDate from, LocalDate to) {
        Long companyId = requireCompany();
        LocalDate today = LocalDate.now();
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom = from != null ? from : effectiveTo.withDayOfMonth(1);

        FinanceReportSummaryDTO snap = financeReportService.buildSummary(effectiveFrom, effectiveTo);
        List<ChartOfAccounts> accounts = coaRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        Map<Long, BigDecimal> debits = toAmountMap(transactionRepo.sumDebitsByAccount(
                companyId, effectiveFrom, effectiveTo));
        Map<Long, BigDecimal> credits = toAmountMap(transactionRepo.sumCreditsByAccount(
                companyId, effectiveFrom, effectiveTo));

        String companyName = null;
        List<FinancialSummaryReportDTO.TrialBalanceRow> tb = new ArrayList<>();
        BigDecimal cash = BigDecimal.ZERO;
        Map<COAType, BigDecimal> closingByType = new HashMap<>();

        for (ChartOfAccounts a : accounts) {
            if (Boolean.FALSE.equals(a.getIsActive())) {
                continue;
            }
            if (companyName == null && a.getCompany() != null) {
                companyName = a.getCompany().getCompanyName();
            }
            BigDecimal dr = debits.getOrDefault(a.getId(), BigDecimal.ZERO);
            BigDecimal cr = credits.getOrDefault(a.getId(), BigDecimal.ZERO);
            BigDecimal closing = nz(a.getBalance());
            BigDecimal opening = closing.subtract(dr).add(cr);
            COAType type = a.getType() != null ? a.getType() : COAType.ASSET;
            closingByType.merge(type, closing, BigDecimal::add);
            if (type == COAType.CASH) {
                cash = cash.add(closing);
            }
            tb.add(FinancialSummaryReportDTO.TrialBalanceRow.builder()
                    .accountId(a.getId())
                    .accountCode(a.getAccountCode())
                    .accountName(a.getAccountName())
                    .accountClass(classLabel(type))
                    .accountType(type.name())
                    .opening(scale(opening))
                    .debits(scale(dr))
                    .credits(scale(cr))
                    .closing(scale(closing))
                    .build());
        }
        tb.sort(Comparator.comparing(r -> r.getAccountCode() == null ? "" : r.getAccountCode()));

        BigDecimal revenue = nz(snap.getTotals() != null ? snap.getTotals().getRevenue() : null);
        BigDecimal expenses = nz(snap.getTotals() != null ? snap.getTotals().getExpenses() : null);
        BigDecimal periodRevenue = BigDecimal.ZERO;
        BigDecimal periodExpenses = BigDecimal.ZERO;
        for (FinancialSummaryReportDTO.TrialBalanceRow r : tb) {
            COAType t = parseType(r.getAccountType());
            if (REVENUE_TYPES.contains(t)) {
                periodRevenue = periodRevenue.add(nz(r.getCredits())).subtract(nz(r.getDebits()));
            }
            if (EXPENSE_TYPES.contains(t)) {
                periodExpenses = periodExpenses.add(nz(r.getDebits())).subtract(nz(r.getCredits()));
            }
        }
        if (periodRevenue.signum() != 0) {
            revenue = periodRevenue;
        }
        if (periodExpenses.signum() != 0) {
            expenses = periodExpenses;
        }
        BigDecimal net = revenue.subtract(expenses);
        BigDecimal margin = revenue.signum() > 0
                ? revenue.subtract(expenses).multiply(BigDecimal.valueOf(100))
                        .divide(revenue, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        if (cash.signum() == 0 && snap.getTotals() != null) {
            cash = nz(snap.getTotals().getCashInflow()).subtract(nz(snap.getTotals().getCashOutflow()));
        }

        FinanceAgingBucketsDTO ar = snap.getArAging();
        FinanceAgingBucketsDTO ap = snap.getApAging();
        BigDecimal overdueAr = ar == null ? BigDecimal.ZERO
                : nz(ar.getD61To90()).add(nz(ar.getD90Plus()));
        BigDecimal overdueAp = ap == null ? BigDecimal.ZERO
                : nz(ap.getD61To90()).add(nz(ap.getD90Plus()));

        List<FinancialSummaryReportDTO.ExceptionRow> exceptions = new ArrayList<>();
        if (overdueAr.signum() > 0) {
            long count = ar == null ? 0 : ar.getD61To90Count() + ar.getD90PlusCount();
            exceptions.add(FinancialSummaryReportDTO.ExceptionRow.builder()
                    .label("Receivables overdue over 60 days")
                    .count(count)
                    .value(scale(overdueAr))
                    .detail("From open sales invoices past due")
                    .owner("Finance Manager")
                    .tone("warn")
                    .build());
        }
        if (overdueAp.signum() > 0) {
            long count = ap == null ? 0 : ap.getD61To90Count() + ap.getD90PlusCount();
            exceptions.add(FinancialSummaryReportDTO.ExceptionRow.builder()
                    .label("Payables overdue over 60 days")
                    .count(count)
                    .value(scale(overdueAp))
                    .detail("From open purchase invoices past due")
                    .owner("AP Accountant")
                    .tone("warn")
                    .build());
        }
        if (exceptions.isEmpty()) {
            exceptions.add(FinancialSummaryReportDTO.ExceptionRow.builder()
                    .label("No ageing exceptions")
                    .count(0)
                    .value(BigDecimal.ZERO)
                    .detail("AR/AP ageing within 60 days")
                    .owner("Chief Accountant")
                    .tone("ok")
                    .build());
        }

        List<FinancialSummaryReportDTO.MonthlyPoint> trend = new ArrayList<>();
        if (snap.getRevenueByMonth() != null) {
            for (var p : snap.getRevenueByMonth()) {
                trend.add(FinancialSummaryReportDTO.MonthlyPoint.builder()
                        .yearMonth(p.getYearMonth())
                        .label(shortMonth(p.getYearMonth()))
                        .value(scale(p.getValue()))
                        .build());
            }
        }

        return FinancialSummaryReportDTO.builder()
                .companyName(companyName)
                .from(effectiveFrom)
                .to(effectiveTo)
                .generatedAt(Instant.now())
                .periodLabel(periodLabel(effectiveFrom, effectiveTo))
                .revenue(scale(revenue))
                .expenses(scale(expenses))
                .netProfit(scale(net))
                .grossMarginPercent(margin)
                .cashBalance(scale(cash))
                .totalReceivables(scale(snap.getTotals() != null ? snap.getTotals().getTotalReceivables() : null))
                .totalPayables(scale(snap.getTotals() != null ? snap.getTotals().getTotalPayables() : null))
                .overdueReceivables(scale(overdueAr))
                .overduePayables(scale(overdueAp))
                .profitAndLoss(buildPl(tb, revenue, expenses, net))
                .balanceSheet(buildBs(closingByType, net))
                .revenueTrend(trend)
                .arAging(toAging(ar))
                .apAging(toAging(ap))
                .exceptions(exceptions)
                .trialBalance(tb)
                .build();
    }

    public AccountSummaryReportDTO accountSummary(Long accountId, LocalDate from, LocalDate to) {
        Long companyId = requireCompany();
        LocalDate today = LocalDate.now();
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusMonths(11).withDayOfMonth(1);

        ChartOfAccounts a = coaRepo.findById(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (a.getCompany() == null || !Objects.equals(a.getCompany().getId(), companyId)) {
            throw new NotFoundException("Account not found");
        }

        Map<Long, BigDecimal> debits = toAmountMap(transactionRepo.sumDebitsByAccount(
                companyId, effectiveFrom, effectiveTo));
        Map<Long, BigDecimal> credits = toAmountMap(transactionRepo.sumCreditsByAccount(
                companyId, effectiveFrom, effectiveTo));
        BigDecimal dr = debits.getOrDefault(accountId, BigDecimal.ZERO);
        BigDecimal cr = credits.getOrDefault(accountId, BigDecimal.ZERO);
        BigDecimal closing = nz(a.getBalance());
        BigDecimal opening = closing.subtract(dr).add(cr);
        COAType type = a.getType() != null ? a.getType() : COAType.ASSET;

        List<Object[]> monthly = transactionRepo.monthlyMovementForAccount(
                companyId, accountId, effectiveFrom, effectiveTo);
        Map<String, BigDecimal> monthNet = new HashMap<>();
        for (Object[] row : monthly) {
            int y = ((Number) row[0]).intValue();
            int m = ((Number) row[1]).intValue();
            BigDecimal mDr = toBd(row[2]);
            BigDecimal mCr = toBd(row[3]);
            monthNet.put(String.format("%04d-%02d", y, m), mDr.subtract(mCr));
        }
        List<AccountSummaryReportDTO.MonthlyPoint> trend = new ArrayList<>();
        YearMonth cursor = YearMonth.from(effectiveFrom);
        YearMonth end = YearMonth.from(effectiveTo);
        while (!cursor.isAfter(end)) {
            String key = cursor.toString();
            trend.add(AccountSummaryReportDTO.MonthlyPoint.builder()
                    .yearMonth(key)
                    .label(cursor.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                    .value(scale(monthNet.getOrDefault(key, BigDecimal.ZERO)))
                    .build());
            cursor = cursor.plusMonths(1);
        }

        List<Transaction> recent = transactionRepo.findRecentForAccount(
                companyId, accountId, effectiveFrom, effectiveTo, PageRequest.of(0, 40));
        List<AccountSummaryReportDTO.TxnLine> txns = new ArrayList<>();
        Instant lastAt = null;
        String lastLabel = null;
        for (Transaction t : recent) {
            boolean isDebit = t.getDebitAccount() != null
                    && Objects.equals(t.getDebitAccount().getId(), accountId);
            BigDecimal amt = nz(t.getAmount());
            txns.add(AccountSummaryReportDTO.TxnLine.builder()
                    .date(t.getTransactionDate())
                    .code(t.getTransactionCode())
                    .narrative(t.getTransactionDescription())
                    .side(isDebit ? "Dr" : "Cr")
                    .debit(isDebit ? scale(amt) : BigDecimal.ZERO)
                    .credit(isDebit ? BigDecimal.ZERO : scale(amt))
                    .build());
            if (lastAt == null && t.getCreatedAt() != null) {
                lastAt = t.getCreatedAt();
                lastLabel = (t.getTransactionDate() != null ? t.getTransactionDate() : "")
                        + (t.getTransactionCode() != null ? " · " + t.getTransactionCode() : "");
            }
        }

        List<AccountSummaryReportDTO.MovementLine> movement = List.of(
                AccountSummaryReportDTO.MovementLine.builder()
                        .label("Opening balance")
                        .amount(scale(opening))
                        .note(effectiveFrom.toString())
                        .total(false)
                        .build(),
                AccountSummaryReportDTO.MovementLine.builder()
                        .label("Period debits")
                        .amount(scale(dr))
                        .note(txns.stream().filter(x -> "Dr".equals(x.getSide())).count() + " lines")
                        .total(false)
                        .build(),
                AccountSummaryReportDTO.MovementLine.builder()
                        .label("Period credits")
                        .amount(scale(cr.negate()))
                        .note(txns.stream().filter(x -> "Cr".equals(x.getSide())).count() + " lines")
                        .total(false)
                        .build(),
                AccountSummaryReportDTO.MovementLine.builder()
                        .label("Closing balance")
                        .amount(scale(closing))
                        .note(effectiveTo.toString())
                        .total(true)
                        .build()
        );

        List<AccountSummaryReportDTO.CostCentreLine> cc = new ArrayList<>();
        if (a.getDepartment() != null) {
            cc.add(AccountSummaryReportDTO.CostCentreLine.builder()
                    .name(a.getDepartment().getDepartmentName())
                    .amount(scale(dr.add(cr)))
                    .share(1d)
                    .build());
        }

        return AccountSummaryReportDTO.builder()
                .accountId(a.getId())
                .accountCode(a.getAccountCode())
                .accountName(a.getAccountName())
                .description(a.getDescription())
                .accountClass(classLabel(type))
                .accountType(type.name())
                .normalBalance(normalBalance(type))
                .statement(statementOf(type))
                .departmentName(a.getDepartment() != null ? a.getDepartment().getDepartmentName() : null)
                .projectCode(a.getProjectCode())
                .active(a.getIsActive())
                .companyName(a.getCompany().getCompanyName())
                .from(effectiveFrom)
                .to(effectiveTo)
                .generatedAt(Instant.now())
                .opening(scale(opening))
                .periodDebits(scale(dr))
                .periodCredits(scale(cr))
                .closing(scale(closing))
                .periodMovement(scale(dr.subtract(cr)))
                .lastPostingAt(lastAt)
                .lastPostingLabel(lastLabel)
                .trend(trend)
                .movement(movement)
                .recentTransactions(txns)
                .byCostCentre(cc)
                .build();
    }

    // ── builders ─────────────────────────────────────────────────────────────

    private List<FinancialSummaryReportDTO.StatementLine> buildPl(
            List<FinancialSummaryReportDTO.TrialBalanceRow> tb,
            BigDecimal revenue,
            BigDecimal expenses,
            BigDecimal net
    ) {
        List<FinancialSummaryReportDTO.StatementLine> lines = new ArrayList<>();
        lines.add(line("Revenue", null, true, false, false));
        for (FinancialSummaryReportDTO.TrialBalanceRow r : tb) {
            if (REVENUE_TYPES.contains(parseType(r.getAccountType()))
                    && (nz(r.getCredits()).signum() != 0 || nz(r.getDebits()).signum() != 0)) {
                BigDecimal amt = nz(r.getCredits()).subtract(nz(r.getDebits()));
                lines.add(line(r.getAccountName(), amt, false, false, true));
            }
        }
        lines.add(line("Total revenue", revenue, false, true, false));
        lines.add(line("Costs & expenses", null, true, false, false));
        for (FinancialSummaryReportDTO.TrialBalanceRow r : tb) {
            if (EXPENSE_TYPES.contains(parseType(r.getAccountType()))
                    && (nz(r.getDebits()).signum() != 0 || nz(r.getCredits()).signum() != 0)) {
                BigDecimal amt = nz(r.getDebits()).subtract(nz(r.getCredits()));
                lines.add(line(r.getAccountName(), amt, false, false, true));
            }
        }
        lines.add(line("Total costs & expenses", expenses, false, true, false));
        lines.add(line("Net profit / (loss)", net, false, true, false));
        return lines;
    }

    private List<FinancialSummaryReportDTO.StatementLine> buildBs(
            Map<COAType, BigDecimal> byType,
            BigDecimal netProfit
    ) {
        List<FinancialSummaryReportDTO.StatementLine> lines = new ArrayList<>();
        BigDecimal assets = sumClosing(byType, ASSET_TYPES);
        BigDecimal liabilities = sumClosing(byType, LIABILITY_TYPES);
        BigDecimal equity = sumClosing(byType, EQUITY_TYPES);
        lines.add(line("ASSETS", null, true, false, false));
        lines.add(line("Cash and bank", byType.getOrDefault(COAType.CASH, BigDecimal.ZERO), false, false, true));
        lines.add(line("Other assets", byType.getOrDefault(COAType.ASSET, BigDecimal.ZERO), false, false, true));
        lines.add(line("Total assets", assets, false, true, false));
        lines.add(line("LIABILITIES", null, true, false, false));
        lines.add(line("Liabilities", byType.getOrDefault(COAType.LIABILITY, BigDecimal.ZERO), false, false, true));
        lines.add(line("Tax", byType.getOrDefault(COAType.TAX, BigDecimal.ZERO), false, false, true));
        lines.add(line("Total liabilities", liabilities, false, true, false));
        lines.add(line("EQUITY", null, true, false, false));
        lines.add(line("Equity accounts", equity, false, false, true));
        lines.add(line("Profit for the period (memo)", netProfit, false, false, true));
        lines.add(line("Total equity (accounts)", equity, false, true, false));
        return lines;
    }

    private static FinancialSummaryReportDTO.StatementLine line(
            String label, BigDecimal amount, boolean header, boolean total, boolean indented) {
        return FinancialSummaryReportDTO.StatementLine.builder()
                .label(label)
                .amount(amount == null ? null : scale(amount))
                .header(header)
                .total(total)
                .indented(indented)
                .build();
    }

    private static FinancialSummaryReportDTO.AgingSplit toAging(FinanceAgingBucketsDTO a) {
        if (a == null) {
            return FinancialSummaryReportDTO.AgingSplit.builder()
                    .current(BigDecimal.ZERO).d1To30(BigDecimal.ZERO).d31To60(BigDecimal.ZERO)
                    .d61To90(BigDecimal.ZERO).d90Plus(BigDecimal.ZERO).total(BigDecimal.ZERO)
                    .build();
        }
        BigDecimal total = nz(a.getCurrent()).add(nz(a.getD1To30())).add(nz(a.getD31To60()))
                .add(nz(a.getD61To90())).add(nz(a.getD90Plus()));
        return FinancialSummaryReportDTO.AgingSplit.builder()
                .current(scale(a.getCurrent()))
                .d1To30(scale(a.getD1To30()))
                .d31To60(scale(a.getD31To60()))
                .d61To90(scale(a.getD61To90()))
                .d90Plus(scale(a.getD90Plus()))
                .total(scale(total))
                .build();
    }

    private Long requireCompany() {
        Long companyId = auth.getCurrentCompanyId();
        if (companyId == null) {
            throw new RuntimeException("User is not associated with a company");
        }
        return companyId;
    }

    private static Map<Long, BigDecimal> toAmountMap(List<Object[]> rows) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row == null || row[0] == null) continue;
            map.put(((Number) row[0]).longValue(), toBd(row[1]));
        }
        return map;
    }

    private static BigDecimal sumClosing(Map<COAType, BigDecimal> byType, Set<COAType> types) {
        BigDecimal s = BigDecimal.ZERO;
        for (COAType t : types) {
            s = s.add(byType.getOrDefault(t, BigDecimal.ZERO));
        }
        return s;
    }

    private static String classLabel(COAType type) {
        return switch (type) {
            case ASSET, CASH -> "Assets";
            case LIABILITY, TAX -> "Liabilities";
            case EQUITY -> "Equity";
            case REVENUE, INCOME -> "Revenue";
            case EXPENSE, COST -> "Costs & expenses";
            case BUDGET -> "Budget";
        };
    }

    private static String normalBalance(COAType type) {
        return switch (type) {
            case ASSET, CASH, EXPENSE, COST -> "Debit";
            default -> "Credit";
        };
    }

    private static String statementOf(COAType type) {
        return switch (type) {
            case REVENUE, INCOME, EXPENSE, COST -> "Profit and loss";
            default -> "Balance sheet";
        };
    }

    private static COAType parseType(String name) {
        try {
            return COAType.valueOf(name);
        } catch (Exception e) {
            return COAType.ASSET;
        }
    }

    private static String periodLabel(LocalDate from, LocalDate to) {
        return from.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " "
                + from.getYear() + " → "
                + to.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " "
                + to.getYear();
    }

    private static String shortMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.length() < 7) return yearMonth;
        try {
            return YearMonth.parse(yearMonth).getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        } catch (Exception e) {
            return yearMonth;
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal scale(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal toBd(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
