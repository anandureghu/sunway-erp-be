package com.erp.controller;

import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.dependent.DependentRequestDTO;
import com.erp.dto.dependent.DependentResponseDTO;
import com.erp.service.EmployeeDependentService;
import com.erp.service.security.annotation.HrPermission;
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
    @HrPermission(module = HrModule.DEPENDENTS, action = {HrAction.CREATE})
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
    @HrPermission(module = HrModule.DEPENDENTS, action = {HrAction.EDIT})
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
    @HrPermission(module = HrModule.DEPENDENTS, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
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
    @HrPermission(module = HrModule.DEPENDENTS, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
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
    @HrPermission(module = HrModule.DEPENDENTS, action = {HrAction.DELETE})
    @DeleteMapping("/{employeeId}/dependents/{dependentId}")
    public ResponseEntity<Void> deleteDependent(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("dependentId") Long dependentId
    ) {
        dependentService.deleteDependent(dependentId);
        return ResponseEntity.noContent().build();
    }
}