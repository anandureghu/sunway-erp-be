package com.erp.controller;

import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.appraisal.EmployeeAppraisalRequestDTO;
import com.erp.dto.appraisal.EmployeeAppraisalResponseDTO;
import com.erp.service.EmployeeAppraisalService;
import com.erp.service.security.annotation.HrPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/appraisals")
@RequiredArgsConstructor
public class EmployeeAppraisalController {

    private final EmployeeAppraisalService appraisalService;

    // ======================================================
    // LIST APPRAISALS
    // VIEW_OWN — user views their own appraisals
    // VIEW_ALL — admin/HR views anyone's appraisals
    // ======================================================
    @HrPermission(module = HrModule.APPRAISAL, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<List<EmployeeAppraisalResponseDTO>> list(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(appraisalService.list(employeeId));
    }

    // ======================================================
    // CREATE APPRAISAL
    // CREATE — adding a new appraisal record
    // ======================================================
    @HrPermission(module = HrModule.APPRAISAL, action = {HrAction.CREATE})
    @PostMapping
    public ResponseEntity<Void> create(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody EmployeeAppraisalRequestDTO dto) {

        appraisalService.create(employeeId, dto);
        return ResponseEntity.ok().build();
    }

    // ======================================================
    // UPDATE APPRAISAL
    // EDIT — updating an existing appraisal
    // APPROVE — approving an appraisal also uses this endpoint
    // ======================================================
    @HrPermission(module = HrModule.APPRAISAL, action = {HrAction.EDIT, HrAction.APPROVE})
    @PutMapping("/{appraisalId}")
    public ResponseEntity<Void> update(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("appraisalId") Long appraisalId,
            @Valid @RequestBody EmployeeAppraisalRequestDTO dto) {

        appraisalService.update(employeeId, appraisalId, dto);
        return ResponseEntity.ok().build();
    }

    // ======================================================
    // DELETE APPRAISAL
    // DELETE — removing an appraisal record
    // ======================================================
    @HrPermission(module = HrModule.APPRAISAL, action = {HrAction.DELETE})
    @DeleteMapping("/{appraisalId}")
    public ResponseEntity<Void> delete(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("appraisalId") Long appraisalId) {

        appraisalService.delete(employeeId, appraisalId);
        return ResponseEntity.noContent().build();
    }
}