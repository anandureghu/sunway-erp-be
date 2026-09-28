package com.erp.dto.finance.report.sheet;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * FIN_SHEET_REPORTS — Financial Summary sheet payload.
 * Delete with FinanceSheetReportService when those report pages are retired.
 */
@Getter
@Builder
public class FinancialSummaryReportDTO {
    private String companyName;
    private LocalDate from;
    private LocalDate to;
    private Instant generatedAt;
    private String periodLabel;

    private BigDecimal revenue;
    private BigDecimal expenses;
    private BigDecimal netProfit;
    private BigDecimal grossMarginPercent;
    private BigDecimal cashBalance;
    private BigDecimal totalReceivables;
    private BigDecimal totalPayables;
    private BigDecimal overdueReceivables;
    private BigDecimal overduePayables;

    private List<StatementLine> profitAndLoss;
    private List<StatementLine> balanceSheet;
    private List<MonthlyPoint> revenueTrend;
    private AgingSplit arAging;
    private AgingSplit apAging;
    private List<ExceptionRow> exceptions;
    private List<TrialBalanceRow> trialBalance;

    @Getter
    @Builder
    public static class StatementLine {
        private String label;
        private BigDecimal amount;
        private boolean header;
        private boolean total;
        private boolean indented;
    }

    @Getter
    @Builder
    public static class MonthlyPoint {
        private String yearMonth;
        private String label;
        private BigDecimal value;
    }

    @Getter
    @Builder
    public static class AgingSplit {
        private BigDecimal current;
        private BigDecimal d1To30;
        private BigDecimal d31To60;
        private BigDecimal d61To90;
        private BigDecimal d90Plus;
        private BigDecimal total;
    }

    @Getter
    @Builder
    public static class ExceptionRow {
        private String label;
        private long count;
        private BigDecimal value;
        private String detail;
        private String owner;
        private String tone; // ok | warn | danger | muted
    }

    @Getter
    @Builder
    public static class TrialBalanceRow {
        private Long accountId;
        private String accountCode;
        private String accountName;
        private String accountClass;
        private String accountType;
        private BigDecimal opening;
        private BigDecimal debits;
        private BigDecimal credits;
        private BigDecimal closing;
    }
}
