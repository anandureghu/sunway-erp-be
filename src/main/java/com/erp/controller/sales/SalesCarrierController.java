package com.erp.controller.sales;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.inventory.DispatchCarrierResponseDTO;
import com.erp.service.inventory.DispatchCarrierService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sales/carriers")
public class SalesCarrierController {

    private final DispatchCarrierService service;

    public SalesCarrierController(DispatchCarrierService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<DispatchCarrierResponseDTO> listActive() {
        return service.listActive();
    }
}
