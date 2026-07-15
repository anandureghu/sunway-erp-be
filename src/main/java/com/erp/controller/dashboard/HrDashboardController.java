package com.erp.controller.dashboard;

import com.erp.dto.dashboard.hr.HrDashboardResponseDTO;
import com.erp.service.dashboard.HrDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/hr")
public class HrDashboardController {

    private final HrDashboardService hrDashboardService;

    public HrDashboardController(HrDashboardService hrDashboardService) {
        this.hrDashboardService = hrDashboardService;
    }

    @GetMapping
    public HrDashboardResponseDTO get() {
        return hrDashboardService.build();
    }
}
