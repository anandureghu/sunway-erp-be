package com.erp.controller;

import com.erp.dto.dependent.DependentRequestDTO;
import com.erp.dto.dependent.DependentResponseDTO;
import com.erp.service.EmployeeDependentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeDependentController {

    private final EmployeeDependentService dependentService;

    @PostMapping("/{employeeId}/dependents")
    public ResponseEntity<DependentResponseDTO> createDependent(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody DependentRequestDTO dto
    ) {
        return ResponseEntity.ok(
                dependentService.createDependent(employeeId, dto)
        );
    }

    @PutMapping("/{employeeId}/dependents/{dependentId}")
    public ResponseEntity<DependentResponseDTO> updateDependent(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("dependentId") Long dependentId,
            @RequestBody DependentRequestDTO dto
    ) {
        return ResponseEntity.ok(
                dependentService.updateDependent(dependentId, dto)
        );
    }

    @GetMapping("/{employeeId}/dependents")
    public ResponseEntity<List<DependentResponseDTO>> getDependents(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(
                dependentService.getDependentsByEmployee(employeeId)
        );
    }

    @GetMapping("/{employeeId}/dependents/{dependentId}")
    public ResponseEntity<DependentResponseDTO> getDependentById(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("dependentId") Long dependentId
    ) {
        return ResponseEntity.ok(
                dependentService.getDependentById(dependentId)
        );
    }

    @DeleteMapping("/{employeeId}/dependents/{dependentId}")
    public ResponseEntity<Void> deleteDependent(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("dependentId") Long dependentId
    ) {
        dependentService.deleteDependent(dependentId);
        return ResponseEntity.noContent().build();
    }
}
