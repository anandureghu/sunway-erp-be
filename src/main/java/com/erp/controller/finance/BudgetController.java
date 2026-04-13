package com.erp.controller.finance;

import com.erp.domain.finance.BudgetStatus;
import com.erp.dto.finance.*;
import com.erp.service.finance.BudgetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance/budgets")
public class BudgetController {

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BudgetResponseDTO> create(@RequestBody BudgetCreateDTO dto) {
        return ResponseEntity.ok(service.createBudget(dto));
    }

    @GetMapping
    public List<BudgetResponseDTO> list() {
        return service.listBudgets();
    }

    @GetMapping("/{id}")
    public ResponseEntity<BudgetResponseDTO> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.getBudget(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponseDTO> updateBudget(
            @PathVariable("id") Long id, @RequestBody BudgetUpdateDTO dto) {
        return ResponseEntity.ok(service.updateBudget(id, dto));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<BudgetResponseDTO> activate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.activate(id));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<BudgetResponseDTO> close(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.close(id));
    }

    @PostMapping("/{id}/hold")
    public ResponseEntity<BudgetResponseDTO> hold(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.hold(id));
    }

    @PostMapping("/{id}/lines")
    public ResponseEntity<BudgetResponseDTO> addLine(
            @PathVariable("id") Long id,
            @RequestBody BudgetLineCreateDTO dto
    ) {
        return ResponseEntity.ok(service.addLine(id, dto));
    }

    @PutMapping("/{id}/lines/{lineId}")
    public ResponseEntity<BudgetResponseDTO> updateLine(
            @PathVariable("id") Long id,
            @PathVariable("lineId") Long lineId,
            @RequestBody BudgetLineUpdateDTO dto
    ) {
        return ResponseEntity.ok(service.updateLine(id, lineId, dto));
    }

    @PatchMapping("/{id}/lines/{lineId}")
    public ResponseEntity<BudgetResponseDTO> updateLineStatus(
            @PathVariable("id") Long id,
            @PathVariable("lineId") Long lineId,
            @RequestParam("status") BudgetStatus status
    ) {
        return ResponseEntity.ok(service.updateLineStatus(id, lineId, status));
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    public ResponseEntity<BudgetResponseDTO> deleteLine(
            @PathVariable("id") Long id,
            @PathVariable("lineId") Long lineId
    ) {
        return ResponseEntity.ok(service.deleteLine(id, lineId));
    }
}

