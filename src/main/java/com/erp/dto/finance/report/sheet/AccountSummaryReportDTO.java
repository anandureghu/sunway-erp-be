package com.erp.dto.finance.report.sheet;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * FIN_SHEET_REPORTS — Account Summary sheet payload.
 * Delete with FinanceSheetReportService when those report pages are retired.
 */
@Getter
@Builder
public class AccountSummaryReportDTO {
    private Long accountId;
    private String accountCode;
    private String accountName;
    private String description;
    private String accountClass;
    private String accountType;
    private String normalBalance;
    private String statement;
    private String departmentName;
    private String projectCode;
    private Boolean active;
    private String companyName;
    private LocalDate from;
    private LocalDate to;
    private Instant generatedAt;

    private BigDecimal opening;
    private BigDecimal periodDebits;
    private BigDecimal periodCredits;
    private BigDecimal closing;
    private BigDecimal periodMovement;
    private Instant lastPostingAt;
    private String lastPostingLabel;

    private List<MonthlyPoint> trend;
    private List<MovementLine> movement;
    private List<TxnLine> recentTransactions;
    private List<CostCentreLine> byCostCentre;

    @Getter
    @Builder
    public static class MonthlyPoint {
        private String yearMonth;
        private String label;
        private BigDecimal value;
    }

    @Getter
    @Builder
    public static class MovementLine {
        private String label;
        private BigDecimal amount;
        private String note;
        private boolean total;
    }

    @Getter
    @Builder
    public static class TxnLine {
        private LocalDate date;
        private String code;
        private String narrative;
        private String side; // Dr | Cr
        private BigDecimal debit;
        private BigDecimal credit;
    }

    @Getter
    @Builder
    public static class CostCentreLine {
        private String name;
        private BigDecimal amount;
        private double share;
    }
}
