package com.erp.controller;

import com.erp.dto.currentjob.EmployeeCurrentJobRequestDTO;
import com.erp.dto.currentjob.EmployeeCurrentJobResponseDTO;
import com.erp.service.CurrentJobService;
import com.erp.service.security.annotation.HrPermission;
import com.erp.domain.security.HrModule;
import com.erp.domain.security.HrAction;
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
    @HrPermission(module = HrModule.CURRENT_JOB, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
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
    @HrPermission(module = HrModule.CURRENT_JOB, action = {HrAction.CREATE})
    @PostMapping
    public ResponseEntity<EmployeeCurrentJobResponseDTO> create(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody EmployeeCurrentJobRequestDTO dto  // ✅ @Valid triggers @NotNull checks
    ) {
        return ResponseEntity.ok(service.create(employeeId, dto));
    }

    // ---------------- UPDATE ----------------
    @HrPermission(module = HrModule.CURRENT_JOB, action = {HrAction.EDIT})
    @PutMapping
    public ResponseEntity<EmployeeCurrentJobResponseDTO> update(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody EmployeeCurrentJobRequestDTO dto  // ✅ @Valid triggers @NotNull checks
    ) {
        return ResponseEntity.ok(service.update(employeeId, dto));
    }
}