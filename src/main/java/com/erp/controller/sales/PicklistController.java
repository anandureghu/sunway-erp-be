package com.erp.controller.sales;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.sales.PicklistResponseDTO;
import com.erp.service.sales.PicklistService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouse/picklists")
public class PicklistController {

    private final PicklistService service;

    public PicklistController(PicklistService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.CREATE, AppAction.EDIT})
    @PostMapping("/from-sales-order/{salesOrderId}")
    public PicklistResponseDTO generate(@PathVariable("salesOrderId") Long salesOrderId) {
        return service.generate(salesOrderId);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT})
    @PostMapping("/{id}/picked")
    public PicklistResponseDTO markPicked(@PathVariable("id") Long id) {
        return service.markPicked(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT, AppAction.DELETE})
    @PostMapping("/{id}/cancel")
    public PicklistResponseDTO cancel(@PathVariable("id") Long id) {
        return service.cancel(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public PicklistResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<PicklistResponseDTO> list() {
        return service.list();
    }
}
