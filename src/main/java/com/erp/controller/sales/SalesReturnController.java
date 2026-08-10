package com.erp.controller.sales;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.sales.CreateSalesReturnDTO;
import com.erp.dto.sales.SalesReturnResponseDTO;
import com.erp.service.sales.SalesReturnService;
import com.erp.service.security.annotation.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales/returns")
@RequiredArgsConstructor
public class SalesReturnController {

    private final SalesReturnService salesReturnService;

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<SalesReturnResponseDTO> list(
            @RequestParam(value = "salesOrderId", required = false) Long salesOrderId
    ) {
        if (salesOrderId != null) {
            return salesReturnService.listForSalesOrder(salesOrderId);
        }
        return salesReturnService.listForCompany();
    }

    @RequiresPermission(module = AppModule.INVENTORY_SALES, action = {AppAction.CREATE, AppAction.EDIT})
    @PostMapping
    public SalesReturnResponseDTO create(@Valid @RequestBody CreateSalesReturnDTO dto) {
        return salesReturnService.create(dto);
    }
}
