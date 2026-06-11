package com.erp.controller.finance.report;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.finance.report.FinanceReportSummaryDTO;
import com.erp.service.finance.report.FinanceReportService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/finance/reports")
public class FinanceReportController {

    private final FinanceReportService financeReportService;

    public FinanceReportController(FinanceReportService financeReportService) {
        this.financeReportService = financeReportService;
    }

    /**
     * Aggregated finance snapshot for the current company:
     * P&L, AR/AP outstanding + aging, cash flow, top customers/vendors, monthly trends.
     *
     * @param from optional start date (inclusive). Defaults to 12 months prior to {@code to}.
     * @param to   optional end date (inclusive). Defaults to today.
     */
    @RequiresPermission(module = AppModule.FINANCE_REPORTS, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/summary")
    public FinanceReportSummaryDTO summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return financeReportService.buildSummary(from, to);
    }
}
