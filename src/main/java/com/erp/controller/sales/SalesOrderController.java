package com.erp.controller.sales;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.common.PageResponse;
import com.erp.dto.sales.SalesOrderCreateDTO;
import com.erp.dto.sales.SalesOrderResponseDTO;
import com.erp.dto.sales.SalesOrderUpdateDTO;
import com.erp.service.finance.InvoiceService;
import com.erp.service.sales.SalesOrderService;
import com.erp.service.security.annotation.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    @GetMapping(params = "page")
    public PageResponse<SalesOrderResponseDTO> listPaged(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo
    ) {
        return service.listPaged(page, size, search, tab, status, paymentStatus, dateFrom, dateTo);
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

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.EDIT, AppAction.APPROVE})
    @PostMapping("/{id}/complete")
    public SalesOrderResponseDTO complete(@PathVariable("id") Long id) {
        return service.complete(id);
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.DELETE})
    @PostMapping("/{id}/archive")
    public SalesOrderResponseDTO archive(@PathVariable Long id) {
        return service.archive(id);
    }
}
