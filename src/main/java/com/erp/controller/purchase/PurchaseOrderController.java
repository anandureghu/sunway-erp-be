package com.erp.controller.purchase;

import com.erp.dto.purchase.PurchaseOrderCreateDTO;
import com.erp.dto.purchase.PurchaseOrderResponseDTO;
import com.erp.dto.purchase.PurchaseOrderUpdateDTO;
import com.erp.service.purchase.PurchaseOrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase/orders")
public class PurchaseOrderController {

    private final PurchaseOrderService service;

    public PurchaseOrderController(PurchaseOrderService service) {
        this.service = service;
    }

    @PostMapping
    public PurchaseOrderResponseDTO create(@RequestBody PurchaseOrderCreateDTO dto) {
        return service.create(dto);
    }

    @PostMapping("/{id}/confirm")
    public PurchaseOrderResponseDTO confirm(@PathVariable("id") Long id) {
        return service.confirm(id);
    }

    @GetMapping("/{id}")
    public PurchaseOrderResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<PurchaseOrderResponseDTO> list() {
        return service.list();
    }

    @PutMapping("/{id}")
    public PurchaseOrderResponseDTO update(
            @PathVariable("id") Long id,
            @RequestBody PurchaseOrderUpdateDTO dto
    ) {
        return service.update(id, dto);
    }

    @PostMapping("/{id}/cancel")
    public PurchaseOrderResponseDTO cancel(@PathVariable("id") Long id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/archive")
    public PurchaseOrderResponseDTO archive(@PathVariable("id") Long id) {
        return service.archive(id);
    }
}
