package com.erp.controller.dashboard;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.dashboard.finance.FinanceDashboardResponseDTO;
import com.erp.service.dashboard.FinanceDashboardService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/finance")
public class FinanceDashboardController {

    private final FinanceDashboardService financeDashboardService;

    public FinanceDashboardController(FinanceDashboardService financeDashboardService) {
        this.financeDashboardService = financeDashboardService;
    }

    @RequiresPermission(module = AppModule.FINANCE_DASHBOARD, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public FinanceDashboardResponseDTO get() {
        return financeDashboardService.build();
    }
}
