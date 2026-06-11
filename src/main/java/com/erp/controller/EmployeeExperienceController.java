package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.currentjob.EmployeeExperienceRequestDTO;
import com.erp.dto.currentjob.EmployeeExperienceResponseDTO;
import com.erp.service.EmployeeExperienceService;
import com.erp.service.security.annotation.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/employees/{employeeId}/experiences")
public class EmployeeExperienceController {

    private final EmployeeExperienceService experienceService;

    // ======================================================
    // GET ALL EXPERIENCES
    // VIEW_OWN — user views their own experiences
    // VIEW_ALL — admin/HR views anyone's experiences
    // ======================================================
    @RequiresPermission(module = AppModule.CURRENT_JOB, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<List<EmployeeExperienceResponseDTO>> getAll(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(experienceService.getAll(employeeId));
    }

    // ======================================================
    // CREATE EXPERIENCE
    // CREATE — adding a new experience record
    // ======================================================
    @RequiresPermission(module = AppModule.CURRENT_JOB, action = {AppAction.CREATE})
    @PostMapping
    public ResponseEntity<EmployeeExperienceResponseDTO> create(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody EmployeeExperienceRequestDTO dto
    ) {
        return ResponseEntity.ok(
                experienceService.create(employeeId, dto)
        );
    }

    // ======================================================
    // UPDATE EXPERIENCE
    // EDIT — updating an existing experience record
    // ======================================================
    @RequiresPermission(module = AppModule.CURRENT_JOB, action = {AppAction.EDIT})
    @PutMapping("/{experienceId}")
    public ResponseEntity<EmployeeExperienceResponseDTO> update(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("experienceId") Long experienceId,
            @Valid @RequestBody EmployeeExperienceRequestDTO dto
    ) {
        return ResponseEntity.ok(
                experienceService.update(employeeId, experienceId, dto)
        );
    }

    // ======================================================
    // DELETE EXPERIENCE
    // DELETE — removing an experience record
    // ======================================================
    @RequiresPermission(module = AppModule.CURRENT_JOB, action = {AppAction.DELETE})
    @DeleteMapping("/{experienceId}")
    public ResponseEntity<Void> delete(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("experienceId") Long experienceId
    ) {
        experienceService.delete(employeeId, experienceId);
        return ResponseEntity.noContent().build();
    }
}