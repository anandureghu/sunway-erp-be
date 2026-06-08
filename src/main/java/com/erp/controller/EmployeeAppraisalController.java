package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.appraisal.EmployeeAppraisalRequestDTO;
import com.erp.dto.appraisal.EmployeeAppraisalResponseDTO;
import com.erp.service.appraisal.EmployeeAppraisalService;
import com.erp.service.security.annotation.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class EmployeeAppraisalController {

    private final EmployeeAppraisalService appraisalService;

    /* =========================================================
       LIST BY EMPLOYEE
       ========================================================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping("/employees/{employeeId}/appraisals")
    public ResponseEntity<Page<EmployeeAppraisalResponseDTO>> listByEmployee(
            @PathVariable("employeeId") Long employeeId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                appraisalService.getAppraisalsByEmployee(employeeId, pageable));
    }

    /* =========================================================
       CREATE — requires year + month
       POST /api/employees/{id}/appraisals?year=2026&month=MARCH
       ========================================================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.CREATE)
    @PostMapping("/employees/{employeeId}/appraisals")
    public ResponseEntity<EmployeeAppraisalResponseDTO> create(
            @PathVariable("employeeId") Long employeeId,
            @RequestParam("year")  Integer year,
            @RequestParam("month") String  month
    ) {
        return ResponseEntity.ok(
                appraisalService.createAppraisal(employeeId, year, month));
    }

    /* =========================================================
       SELF SUBMIT  (DRAFT → SELF_SUBMITTED)
       ========================================================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.EDIT)
    @PutMapping("/employees/{employeeId}/appraisals/{appraisalId}/self-submit")
    public ResponseEntity<EmployeeAppraisalResponseDTO> selfSubmit(
            @PathVariable("employeeId")   Long employeeId,
            @PathVariable("appraisalId")  Long appraisalId,
            @Valid @RequestBody EmployeeAppraisalRequestDTO dto
    ) {
        return ResponseEntity.ok(
                appraisalService.submitSelfAssessment(employeeId, appraisalId, dto));
    }

    /* =========================================================
       MANAGER REVIEW  (SELF_SUBMITTED → MANAGER_REVIEWED)
       ========================================================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.APPROVE)
    @PutMapping("/employees/{employeeId}/appraisals/{appraisalId}/manager-review")
    public ResponseEntity<EmployeeAppraisalResponseDTO> managerReview(
            @PathVariable("employeeId")   Long employeeId,
            @PathVariable("appraisalId")  Long appraisalId,
            @Valid @RequestBody EmployeeAppraisalRequestDTO dto
    ) {
        return ResponseEntity.ok(
                appraisalService.submitManagerReview(employeeId, appraisalId, dto));
    }

    /* =========================================================
       LOCK  (MANAGER_REVIEWED → LOCKED)
       ========================================================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.APPROVE)
    @PutMapping("/employees/{employeeId}/appraisals/{appraisalId}/lock")
    public ResponseEntity<EmployeeAppraisalResponseDTO> lock(
            @PathVariable("employeeId")  Long employeeId,
            @PathVariable("appraisalId") Long appraisalId
    ) {
        return ResponseEntity.ok(
                appraisalService.lockAppraisal(employeeId, appraisalId));
    }

    /* =========================================================
       UNLOCK  (LOCKED → MANAGER_REVIEWED)
       ========================================================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.APPROVE)
    @PutMapping("/employees/{employeeId}/appraisals/{appraisalId}/unlock")
    public ResponseEntity<EmployeeAppraisalResponseDTO> unlock(
            @PathVariable("employeeId")  Long employeeId,
            @PathVariable("appraisalId") Long appraisalId
    ) {
        return ResponseEntity.ok(
                appraisalService.unlockAppraisal(employeeId, appraisalId));
    }

    /* =========================================================
       DELETE  (blocked if LOCKED)
       ========================================================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.DELETE)
    @DeleteMapping("/employees/{employeeId}/appraisals/{appraisalId}")
    public ResponseEntity<Void> delete(
            @PathVariable("employeeId")  Long employeeId,
            @PathVariable("appraisalId") Long appraisalId
    ) {
        appraisalService.delete(employeeId, appraisalId);
        return ResponseEntity.noContent().build();
    }

    /* =========================================================
       FORCE DELETE — any status
       ========================================================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.DELETE)
    @DeleteMapping("/employees/{employeeId}/appraisals/{appraisalId}/force")
    public ResponseEntity<Void> forceDelete(
            @PathVariable("employeeId")  Long employeeId,
            @PathVariable("appraisalId") Long appraisalId
    ) {
        appraisalService.forceDelete(employeeId, appraisalId);
        return ResponseEntity.noContent().build();
    }

    /* =========================================================
       GLOBAL VIEW (HR / ADMIN)
       ========================================================= */

    @RequiresPermission(module = AppModule.APPRAISAL, action = AppAction.VIEW_ALL)
    @GetMapping("/appraisals")
    public ResponseEntity<Page<EmployeeAppraisalResponseDTO>> listByYear(
            @RequestParam("year") Integer year,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                appraisalService.getAppraisalsByYear(year, pageable));
    }
}