package com.erp.controller;

import com.erp.dto.currentjob.EmployeeEducationRequestDTO;
import com.erp.dto.currentjob.EmployeeEducationResponseDTO;
import com.erp.service.EmployeeEducationService;
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

    @GetMapping
    public ResponseEntity<List<EmployeeEducationResponseDTO>> getAll(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(service.getAll(employeeId));
    }

    @PostMapping
    public ResponseEntity<EmployeeEducationResponseDTO> create(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody EmployeeEducationRequestDTO dto
    ) {
        return ResponseEntity.ok(service.create(employeeId, dto));
    }

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

    @DeleteMapping("/{educationId}")
    public ResponseEntity<Void> delete(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("educationId") Long educationId
    ) {
        service.delete(employeeId, educationId);
        return ResponseEntity.noContent().build();
    }
}
