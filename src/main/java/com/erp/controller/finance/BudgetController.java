package com.erp.controller.finance;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.finance.*;
import com.erp.service.finance.BudgetService;
import com.erp.service.security.annotation.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance/budgets")
public class BudgetController {

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @RequiresPermission(module = AppModule.FINANCE_BUDGET, action = {AppAction.CREATE})
    @PostMapping
    public ResponseEntity<BudgetResponseDTO> create(@RequestBody BudgetCreateDTO dto) {
        return ResponseEntity.ok(service.createBudget(dto));
    }

    @RequiresPermission(module = AppModule.FINANCE_BUDGET, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping
    public List<BudgetResponseDTO> list() {
        return service.listBudgets();
    }

    @RequiresPermission(module = AppModule.FINANCE_BUDGET, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}")
    public ResponseEntity<BudgetResponseDTO> get(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.getBudget(id));
    }

    @RequiresPermission(module = AppModule.FINANCE_BUDGET, action = {AppAction.EDIT})
    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponseDTO> updateBudget(
            @PathVariable("id") Long id, @RequestBody BudgetUpdateDTO dto) {
        return ResponseEntity.ok(service.updateBudget(id, dto));
    }

    @RequiresPermission(module = AppModule.FINANCE_BUDGET, action = {AppAction.EDIT})
    @PostMapping("/{id}/activate")
    public ResponseEntity<BudgetResponseDTO> activate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.activate(id));
    }

    @RequiresPermission(module = AppModule.FINANCE_BUDGET, action = {AppAction.EDIT})
    @PostMapping("/{id}/close")
    public ResponseEntity<BudgetResponseDTO> close(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.close(id));
    }

    @RequiresPermission(module = AppModule.FINANCE_BUDGET, action = {AppAction.EDIT})
    @PostMapping("/{id}/hold")
    public ResponseEntity<BudgetResponseDTO> hold(@PathVariable("id") Long id) {
        return ResponseEntity.ok(service.hold(id));
    }

    @RequiresPermission(module = AppModule.FINANCE_BUDGET, action = {AppAction.CREATE})
    @PostMapping("/{id}/distribute")
    public ResponseEntity<BudgetResponseDTO> distribute(
            @PathVariable("id") Long id,
            @RequestBody BudgetDistributeDTO dto) {
        return ResponseEntity.ok(service.distributeBudget(id, dto));
    }

    @RequiresPermission(module = AppModule.FINANCE_BUDGET, action = {AppAction.VIEW_ALL, AppAction.VIEW_OWN})
    @GetMapping("/{id}/distributions")
    public List<BudgetDistributionResponseDTO> listDistributions(
            @PathVariable("id") Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean archived) {
        return service.listDistributions(id, from, to, archived);
    }

    @RequiresPermission(module = AppModule.FINANCE_BUDGET, action = {AppAction.EDIT})
    @PostMapping("/{id}/distributions/archive")
    public ResponseEntity<Integer> archiveDistributions(
            @PathVariable("id") Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(service.archiveDistributions(id, from, to));
    }
}
