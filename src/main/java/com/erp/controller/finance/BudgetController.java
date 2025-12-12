package com.erp.controller.finance;

import com.erp.dto.finance.BudgetCreateDTO;
import com.erp.dto.finance.BudgetResponseDTO;
import com.erp.dto.finance.BudgetUpdateDTO;
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
    public ResponseEntity<BudgetResponseDTO> update(@PathVariable("id") Long id, @RequestBody BudgetUpdateDTO dto) {
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
}

