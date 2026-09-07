package com.erp.controller.dashboard;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.dashboard.inventory.InventoryDashboardResponseDTO;
import com.erp.service.dashboard.InventoryDashboardService;
import com.erp.service.security.annotation.RequiresPermission;
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

    @RequiresPermission(module = AppModule.INVENTORY_DASHBOARD, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public InventoryDashboardResponseDTO get() {
        return inventoryDashboardService.build();
    }
}
