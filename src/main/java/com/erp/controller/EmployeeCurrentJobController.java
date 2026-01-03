package com.erp.controller;

import com.erp.dto.currentjob.EmployeeCurrentJobRequestDTO;
import com.erp.dto.currentjob.EmployeeCurrentJobResponseDTO;
import com.erp.service.CurrentJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees/{employeeId}/current-job")
@RequiredArgsConstructor
public class EmployeeCurrentJobController {

    private final CurrentJobService service;

    // ---------------- GET ----------------
    @GetMapping
    public ResponseEntity<EmployeeCurrentJobResponseDTO> get(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(service.get(employeeId));
    }

    // ---------------- CREATE ----------------
    @PostMapping
    public ResponseEntity<EmployeeCurrentJobResponseDTO> create(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody EmployeeCurrentJobRequestDTO dto
    ) {
        return ResponseEntity.ok(service.create(employeeId, dto));
    }

    // ---------------- UPDATE ----------------
    @PutMapping
    public ResponseEntity<EmployeeCurrentJobResponseDTO> update(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody EmployeeCurrentJobRequestDTO dto
    ) {
        return ResponseEntity.ok(service.update(employeeId, dto));
    }
}
