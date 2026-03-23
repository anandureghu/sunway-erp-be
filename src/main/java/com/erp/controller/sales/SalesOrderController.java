package com.erp.controller.sales;

import com.erp.dto.sales.SalesOrderCreateDTO;
import com.erp.dto.sales.SalesOrderResponseDTO;
import com.erp.dto.sales.SalesOrderUpdateDTO;
import com.erp.service.finance.InvoiceService;
import com.erp.service.sales.SalesOrderService;
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

    @PostMapping
    public SalesOrderResponseDTO create(@Valid @RequestBody SalesOrderCreateDTO dto) {
        return service.create(dto);
    }

    @PostMapping("/{id}/confirm")
    public SalesOrderResponseDTO confirm(@PathVariable("id") Long id) {
        SalesOrderResponseDTO confirmed = service.confirm(id);
        invoiceService.createInvoiceForConfirmedSalesOrder(id);
        return confirmed;
    }

    @GetMapping("/{id}")
    public SalesOrderResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<SalesOrderResponseDTO> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    public SalesOrderResponseDTO update(
            @PathVariable Long id,
            @RequestBody SalesOrderUpdateDTO dto
    ) {
        return service.update(id, dto);
    }

    @PostMapping("/{id}/cancel")
    public SalesOrderResponseDTO cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
