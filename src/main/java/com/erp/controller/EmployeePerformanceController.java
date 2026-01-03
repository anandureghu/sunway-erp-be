package com.erp.controller;

import com.erp.dto.appraisal.EmployeePerformanceRequestDTO;
import com.erp.dto.appraisal.EmployeePerformanceResponseDTO;
import com.erp.service.EmployeePerformanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeePerformanceController {

    private final EmployeePerformanceService performanceService;

    // ------------------------------------------------
    // GET PERFORMANCE
    // ------------------------------------------------
    @GetMapping("/{employeeId}/performance")
    public ResponseEntity<EmployeePerformanceResponseDTO> get(
            @PathVariable("employeeId") Long employeeId,
            @RequestParam("month") String month,
            @RequestParam("year") Integer year
    ) {
        return ResponseEntity.ok(
                performanceService.get(employeeId, month, year)
        );
    }

    // ------------------------------------------------
    // CREATE PERFORMANCE
    // ------------------------------------------------
    @PostMapping("/{employeeId}/performance")
    public ResponseEntity<EmployeePerformanceResponseDTO> create(
            @PathVariable("employeeId") Long employeeId,
            @RequestParam("month") String month,
            @RequestParam("year") Integer year,
            @RequestBody EmployeePerformanceRequestDTO dto
    ) {
        return ResponseEntity.ok(
                performanceService.create(employeeId, month, year, dto)
        );
    }

    // ------------------------------------------------
    // UPDATE PERFORMANCE
    // ------------------------------------------------
    @PutMapping("/{employeeId}/performance")
    public ResponseEntity<EmployeePerformanceResponseDTO> update(
            @PathVariable("employeeId") Long employeeId,
            @RequestParam("month") String month,
            @RequestParam("year") Integer year,
            @RequestBody EmployeePerformanceRequestDTO dto
    ) {
        return ResponseEntity.ok(
                performanceService.update(employeeId, month, year, dto)
        );
    }
}
