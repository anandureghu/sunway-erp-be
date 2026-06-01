package com.erp.controller.inventory;

import com.erp.dto.inventory.StockVarianceCreateDTO;
import com.erp.dto.inventory.StockVarianceResponseDTO;
import com.erp.service.inventory.StockVarianceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/variances")
public class StockVarianceController {

    private final StockVarianceService service;

    public StockVarianceController(StockVarianceService service) {
        this.service = service;
    }

    @PostMapping
    public StockVarianceResponseDTO create(@RequestBody StockVarianceCreateDTO dto) {
        return service.create(dto);
    }

    @GetMapping("/pending")
    public List<StockVarianceResponseDTO> listPending() {
        return service.listPending();
    }

    @GetMapping("/history")
    public List<StockVarianceResponseDTO> listHistory() {
        return service.listHistory();
    }

    @GetMapping("/can-approve")
    public Map<String, Boolean> canApprove() {
        return Map.of("canApprove", service.canCurrentUserApprove());
    }

    @PostMapping("/{id}/approve")
    public StockVarianceResponseDTO approve(@PathVariable("id") Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/reject")
    public StockVarianceResponseDTO reject(@PathVariable("id") Long id) {
        return service.reject(id);
    }
}
