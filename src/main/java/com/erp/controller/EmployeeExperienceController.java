package com.erp.controller;

import com.erp.dto.currentjob.EmployeeExperienceRequestDTO;
import com.erp.dto.currentjob.EmployeeExperienceResponseDTO;
import com.erp.service.EmployeeExperienceService;
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

    @GetMapping
    public ResponseEntity<List<EmployeeExperienceResponseDTO>> getAll(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(experienceService.getAll(employeeId));
    }

    @PostMapping
    public ResponseEntity<EmployeeExperienceResponseDTO> create(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody EmployeeExperienceRequestDTO dto
    ) {
        return ResponseEntity.ok(
                experienceService.create(employeeId, dto)
        );
    }

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

    @DeleteMapping("/{experienceId}")
    public ResponseEntity<Void> delete(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("experienceId") Long experienceId
    ) {
        experienceService.delete(employeeId, experienceId);
        return ResponseEntity.noContent().build();
    }
}
