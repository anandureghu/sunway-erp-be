package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.currentjob.EmployeeEducationRequestDTO;
import com.erp.dto.currentjob.EmployeeEducationResponseDTO;
import com.erp.service.EmployeeEducationService;
import com.erp.service.security.annotation.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/educations")
@RequiredArgsConstructor
public class EmployeeEducationController {

    private final EmployeeEducationService service;

    // ======================================================
    // GET ALL EDUCATIONS
    // VIEW_OWN — user views their own education records
    // VIEW_ALL — admin/HR views anyone's education records
    // ======================================================
    @RequiresPermission(module = AppModule.CURRENT_JOB, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<List<EmployeeEducationResponseDTO>> getAll(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(service.getAll(employeeId));
    }

    // ======================================================
    // CREATE EDUCATION
    // CREATE — adding a new education record
    // ======================================================
    @RequiresPermission(module = AppModule.CURRENT_JOB, action = {AppAction.CREATE})
    @PostMapping
    public ResponseEntity<EmployeeEducationResponseDTO> create(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody EmployeeEducationRequestDTO dto
    ) {
        return ResponseEntity.ok(service.create(employeeId, dto));
    }

    // ======================================================
    // UPDATE EDUCATION
    // EDIT — updating an existing education record
    // ======================================================
    @RequiresPermission(module = AppModule.CURRENT_JOB, action = {AppAction.EDIT})
    @PutMapping("/{educationId}")
    public ResponseEntity<EmployeeEducationResponseDTO> update(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("educationId") Long educationId,
            @Valid @RequestBody EmployeeEducationRequestDTO dto
    ) {
        return ResponseEntity.ok(
                service.update(employeeId, educationId, dto)
        );
    }

    // ======================================================
    // DELETE EDUCATION
    // DELETE — removing an education record
    // ======================================================
    @RequiresPermission(module = AppModule.CURRENT_JOB, action = {AppAction.DELETE})
    @DeleteMapping("/{educationId}")
    public ResponseEntity<Void> delete(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("educationId") Long educationId
    ) {
        service.delete(employeeId, educationId);
        return ResponseEntity.noContent().build();
    }
}