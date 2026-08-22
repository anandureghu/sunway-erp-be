package com.erp.controller.sales;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.sales.SalesOrderCreateDTO;
import com.erp.dto.sales.SalesOrderResponseDTO;
import com.erp.dto.sales.SalesOrderUpdateDTO;
import com.erp.service.finance.InvoiceService;
import com.erp.service.sales.SalesOrderService;
import com.erp.service.security.annotation.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales/orders")
public class SalesOrderController {

    private final SalesOrderService service;
    private final InvoiceService invoiceService;

    public SalesOrderController(SalesOrderService service, InvoiceService invoiceService) {
        this.service = service;
        this.invoiceService = invoiceService;
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.CREATE})
    @PostMapping
    public SalesOrderResponseDTO create(@Valid @RequestBody SalesOrderCreateDTO dto) {
        return service.create(dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.APPROVE, AppAction.EDIT})
    @PostMapping("/{id}/confirm")
    public SalesOrderResponseDTO confirm(@PathVariable("id") Long id) {
        return invoiceService.confirmSalesOrderWithInvoice(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public SalesOrderResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<SalesOrderResponseDTO> list() {
        return service.list();
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public SalesOrderResponseDTO update(
            @PathVariable Long id,
            @Valid @RequestBody SalesOrderUpdateDTO dto
    ) {
        return service.update(id, dto);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT, AppAction.DELETE})
    @PostMapping("/{id}/cancel")
    public SalesOrderResponseDTO cancel(@PathVariable Long id) {
        SalesOrderResponseDTO cancelled = service.cancel(id);
        invoiceService.handleSalesOrderCancellation(id);
        return cancelled;
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.DELETE})
    @PostMapping("/{id}/archive")
    public SalesOrderResponseDTO archive(@PathVariable Long id) {
        return service.archive(id);
    }
}
