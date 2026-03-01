package com.erp.controller;

import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.immigration.ResidencePermitRequestDTO;
import com.erp.dto.immigration.ResidencePermitResponseDTO;
import com.erp.service.ResidencePermitService;
import com.erp.service.security.annotation.HrPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeResidencePermitController {

    private final ResidencePermitService permitService;

    // ======================================================
    // GET RESIDENCE PERMIT
    // VIEW_OWN — user views their own permit
    // VIEW_ALL — admin/HR views anyone's permit
    // ======================================================
    @HrPermission(module = HrModule.IMMIGRATION, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping("/{employeeId}/residence-permit")
    public ResponseEntity<ResidencePermitResponseDTO> get(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(
                permitService.getByEmployee(employeeId)
        );
    }

    // ======================================================
    // CREATE RESIDENCE PERMIT
    // CREATE — adding a new residence permit record
    // ======================================================
    @HrPermission(module = HrModule.IMMIGRATION, action = {HrAction.CREATE})
    @PostMapping("/{employeeId}/residence-permit")
    public ResponseEntity<ResidencePermitResponseDTO> create(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody ResidencePermitRequestDTO dto
    ) {
        dto.setEmployeeId(employeeId);
        return ResponseEntity.ok(
                permitService.create(dto)
        );
    }

    // ======================================================
    // UPDATE RESIDENCE PERMIT
    // EDIT — updating an existing residence permit
    // ======================================================
    @HrPermission(module = HrModule.IMMIGRATION, action = {HrAction.EDIT})
    @PutMapping("/{employeeId}/residence-permit")
    public ResponseEntity<ResidencePermitResponseDTO> update(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody ResidencePermitRequestDTO dto
    ) {
        dto.setEmployeeId(employeeId);
        return ResponseEntity.ok(
                permitService.update(dto)
        );
    }

    // ======================================================
    // DELETE RESIDENCE PERMIT
    // DELETE — removing a residence permit record
    // ======================================================
    @HrPermission(module = HrModule.IMMIGRATION, action = {HrAction.DELETE})
    @DeleteMapping("/{employeeId}/residence-permit")
    public ResponseEntity<Void> delete(
            @PathVariable("employeeId") Long employeeId
    ) {
        permitService.deleteByEmployee(employeeId);
        return ResponseEntity.noContent().build();
    }
}