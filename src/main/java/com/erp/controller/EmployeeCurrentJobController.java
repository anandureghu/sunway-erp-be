package com.erp.controller;

import com.erp.dto.currentjob.EmployeeCurrentJobRequestDTO;
import com.erp.dto.currentjob.EmployeeCurrentJobResponseDTO;
import com.erp.service.CurrentJobService;
import com.erp.service.security.annotation.RequiresPermission;
import com.erp.domain.security.AppModule;
import com.erp.domain.security.AppAction;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/{employeeId}/current-job")
@RequiredArgsConstructor
public class EmployeeCurrentJobController {

    private final CurrentJobService service;

    // ---------------- GET ----------------
    // VIEW_OWN — user views their own current job
    // VIEW_ALL — admin/HR views anyone's current job
    @RequiresPermission(module = AppModule.CURRENT_JOB, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<EmployeeCurrentJobResponseDTO> get(
            @PathVariable("employeeId") Long employeeId
    ) {
        EmployeeCurrentJobResponseDTO result = service.get(employeeId);
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    // ---------------- CREATE ----------------
    @RequiresPermission(module = AppModule.CURRENT_JOB, action = {AppAction.CREATE})
    @PostMapping
    public ResponseEntity<EmployeeCurrentJobResponseDTO> create(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody EmployeeCurrentJobRequestDTO dto  // ✅ @Valid triggers @NotNull checks
    ) {
        return ResponseEntity.ok(service.create(employeeId, dto));
    }

    // ---------------- UPDATE ----------------
    @RequiresPermission(module = AppModule.CURRENT_JOB, action = {AppAction.EDIT})
    @PutMapping
    public ResponseEntity<EmployeeCurrentJobResponseDTO> update(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody EmployeeCurrentJobRequestDTO dto  // ✅ @Valid triggers @NotNull checks
    ) {
        return ResponseEntity.ok(service.update(employeeId, dto));
    }
}