package com.erp.controller;

import com.erp.dto.appraisal.EmployeeAppraisalRequestDTO;
import com.erp.dto.appraisal.EmployeeAppraisalResponseDTO;
import com.erp.service.EmployeeAppraisalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/appraisals")
@RequiredArgsConstructor
public class EmployeeAppraisalController {

    private final EmployeeAppraisalService appraisalService;

    /* =====================
       LIST
    ====================== */
    @GetMapping
    public List<EmployeeAppraisalResponseDTO> list(
            @PathVariable("employeeId") Long employeeId) {

        return appraisalService.list(employeeId);
    }

    /* =====================
       CREATE
    ====================== */
    @PostMapping
    public ResponseEntity<Void> create(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody EmployeeAppraisalRequestDTO dto) {

        appraisalService.create(employeeId, dto);
        return ResponseEntity.ok().build();
    }

    /* =====================
       UPDATE
    ====================== */
    @PutMapping("/{appraisalId}")
    public ResponseEntity<Void> update(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("appraisalId") Long appraisalId,
            @RequestBody EmployeeAppraisalRequestDTO dto) {

        appraisalService.update(employeeId, appraisalId, dto);
        return ResponseEntity.ok().build();
    }

    /* =====================
       DELETE
    ====================== */
    @DeleteMapping("/{appraisalId}")
    public ResponseEntity<Void> delete(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("appraisalId") Long appraisalId) {

        appraisalService.delete(employeeId, appraisalId);
        return ResponseEntity.noContent().build();
    }
}
