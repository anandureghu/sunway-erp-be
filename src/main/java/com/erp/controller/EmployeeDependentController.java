package com.erp.controller;

import com.erp.dto.dependent.DependentRequestDTO;
import com.erp.dto.dependent.DependentResponseDTO;
import com.erp.service.EmployeeDependentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/dependents")
@RequiredArgsConstructor
public class EmployeeDependentController {

    private final EmployeeDependentService dependentService;

    @PostMapping
    public ResponseEntity<DependentResponseDTO> create(@RequestBody DependentRequestDTO dto) {
        return ResponseEntity.ok(dependentService.createDependent(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DependentResponseDTO> update(
            @PathVariable Long id,
            @RequestBody DependentRequestDTO dto) {

        return ResponseEntity.ok(dependentService.updateDependent(id, dto));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<DependentResponseDTO>> listByEmployee(@PathVariable Long employeeId) {
        return ResponseEntity.ok(dependentService.getDependentsByEmployee(employeeId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DependentResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(dependentService.getDependentById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        dependentService.deleteDependent(id);
        return ResponseEntity.ok("Dependent deleted successfully");
    }
}
