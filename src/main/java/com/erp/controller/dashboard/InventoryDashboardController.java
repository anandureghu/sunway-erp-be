package com.erp.controller.dashboard;

import com.erp.dto.dashboard.inventory.InventoryDashboardResponseDTO;
import com.erp.service.dashboard.InventoryDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard/inventory")
public class InventoryDashboardController {

    private final InventoryDashboardService inventoryDashboardService;

    public InventoryDashboardController(InventoryDashboardService inventoryDashboardService) {
        this.inventoryDashboardService = inventoryDashboardService;
    }

    @GetMapping
    public InventoryDashboardResponseDTO get() {
        return inventoryDashboardService.build();
    }
}
