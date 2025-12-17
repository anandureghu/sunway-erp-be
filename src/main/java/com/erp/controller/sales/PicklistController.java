package com.erp.controller.sales;

import com.erp.dto.sales.PicklistResponseDTO;
import com.erp.service.sales.PicklistService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/warehouse/picklists")
public class PicklistController {

    private final PicklistService service;

    public PicklistController(PicklistService service) {
        this.service = service;
    }

    @PostMapping("/from-sales-order/{salesOrderId}")
    public PicklistResponseDTO generate(@PathVariable("salesOrderId") Long salesOrderId) {
        return service.generate(salesOrderId);
    }

    @PostMapping("/{id}/picked")
    public PicklistResponseDTO markPicked(@PathVariable("id") Long id) {
        return service.markPicked(id);
    }

    @PostMapping("/{id}/cancel")
    public PicklistResponseDTO cancel(@PathVariable("id") Long id) {
        return service.cancel(id);
    }

    @GetMapping("/{id}")
    public PicklistResponseDTO get(@PathVariable("id") Long id) {
        return service.get(id);
    }

    @GetMapping
    public List<PicklistResponseDTO> list() {
        return service.list();
    }
}
