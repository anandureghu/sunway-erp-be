package com.erp.controller.dashboard;

import com.erp.dto.dashboard.finance.FinanceDashboardResponseDTO;
import com.erp.service.dashboard.FinanceDashboardService;
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

    @GetMapping
    public FinanceDashboardResponseDTO get() {
        return financeDashboardService.build();
    }
}
