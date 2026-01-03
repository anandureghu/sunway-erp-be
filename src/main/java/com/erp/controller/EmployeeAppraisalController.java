package com.erp.controller;

import com.erp.dto.appraisal.EmployeeAppraisalRequestDTO;
import com.erp.dto.appraisal.EmployeeAppraisalResponseDTO;
import com.erp.service.EmployeeAppraisalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeAppraisalController {

    private final EmployeeAppraisalService appraisalService;

    // ---------- GET ----------
    @GetMapping("/{employeeId}/appraisal")
    public ResponseEntity<EmployeeAppraisalResponseDTO> get(
            @PathVariable Long employeeId,
            @RequestParam String month,
            @RequestParam Integer year
    ) {
        return ResponseEntity.ok(
                appraisalService.get(employeeId, month.toLowerCase(), year)
        );
    }

    // ---------- CREATE ----------
    @PostMapping("/{employeeId}/appraisal")
    public ResponseEntity<EmployeeAppraisalResponseDTO> create(
            @PathVariable Long employeeId,
            @RequestParam String month,
            @RequestParam Integer year,
            @RequestBody EmployeeAppraisalRequestDTO dto
    ) {
        return ResponseEntity.ok(
                appraisalService.create(employeeId, month.toLowerCase(), year, dto)
        );
    }

    // ---------- UPDATE ----------
    @PutMapping("/{employeeId}/appraisal")
    public ResponseEntity<EmployeeAppraisalResponseDTO> update(
            @PathVariable Long employeeId,
            @RequestParam String month,
            @RequestParam Integer year,
            @RequestBody EmployeeAppraisalRequestDTO dto
    ) {
        return ResponseEntity.ok(
                appraisalService.update(employeeId, month.toLowerCase(), year, dto)
        );
    }
}
