package com.erp.controller;

import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.currentjob.EmployeeExperienceRequestDTO;
import com.erp.dto.currentjob.EmployeeExperienceResponseDTO;
import com.erp.service.EmployeeExperienceService;
import com.erp.service.security.annotation.HrPermission;
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
    @HrPermission(module = HrModule.CURRENT_JOB, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
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
    @HrPermission(module = HrModule.CURRENT_JOB, action = {HrAction.CREATE})
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
    @HrPermission(module = HrModule.CURRENT_JOB, action = {HrAction.EDIT})
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
    @HrPermission(module = HrModule.CURRENT_JOB, action = {HrAction.DELETE})
    @DeleteMapping("/{experienceId}")
    public ResponseEntity<Void> delete(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("experienceId") Long experienceId
    ) {
        experienceService.delete(employeeId, experienceId);
        return ResponseEntity.noContent().build();
    }
}