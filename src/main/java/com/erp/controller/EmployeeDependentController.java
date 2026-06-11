package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.dependent.DependentRequestDTO;
import com.erp.dto.dependent.DependentResponseDTO;
import com.erp.service.EmployeeDependentService;
import com.erp.service.security.annotation.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeDependentController {

    private final EmployeeDependentService dependentService;

    // ======================================================
    // CREATE DEPENDENT
    // EDIT — adding a dependent is an edit operation on employee
    // ======================================================
    @RequiresPermission(module = AppModule.DEPENDENTS, action = {AppAction.CREATE})
    @PostMapping("/{employeeId}/dependents")
    public ResponseEntity<DependentResponseDTO> createDependent(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody DependentRequestDTO dto
    ) {
        return ResponseEntity.ok(
                dependentService.createDependent(employeeId, dto)
        );
    }

    // ======================================================
    // UPDATE DEPENDENT
    // EDIT — updating a dependent requires edit permission
    // ======================================================
    @RequiresPermission(module = AppModule.DEPENDENTS, action = {AppAction.EDIT})
    @PutMapping("/{employeeId}/dependents/{dependentId}")
    public ResponseEntity<DependentResponseDTO> updateDependent(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("dependentId") Long dependentId,
            @Valid @RequestBody DependentRequestDTO dto
    ) {
        return ResponseEntity.ok(
                dependentService.updateDependent(dependentId, dto)
        );
    }

    // ======================================================
    // GET ALL DEPENDENTS
    // VIEW_OWN — user can view their own dependents
    // VIEW_ALL — admin/HR can view anyone's dependents
    // ======================================================
    @RequiresPermission(module = AppModule.DEPENDENTS, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping("/{employeeId}/dependents")
    public ResponseEntity<List<DependentResponseDTO>> getDependents(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(
                dependentService.getDependentsByEmployee(employeeId)
        );
    }

    // ======================================================
    // GET DEPENDENT BY ID
    // VIEW_OWN — user can view their own dependent details
    // VIEW_ALL — admin/HR can view anyone's dependent details
    // ======================================================
    @RequiresPermission(module = AppModule.DEPENDENTS, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping("/{employeeId}/dependents/{dependentId}")
    public ResponseEntity<DependentResponseDTO> getDependentById(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("dependentId") Long dependentId
    ) {
        return ResponseEntity.ok(
                dependentService.getDependentById(dependentId)
        );
    }

    // ======================================================
    // DELETE DEPENDENT
    // DELETE — removing a dependent requires delete permission
    // ======================================================
    @RequiresPermission(module = AppModule.DEPENDENTS, action = {AppAction.DELETE})
    @DeleteMapping("/{employeeId}/dependents/{dependentId}")
    public ResponseEntity<Void> deleteDependent(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("dependentId") Long dependentId
    ) {
        dependentService.deleteDependent(dependentId);
        return ResponseEntity.noContent().build();
    }
}