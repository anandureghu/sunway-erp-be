package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.immigration.ResidencePermitRequestDTO;
import com.erp.dto.immigration.ResidencePermitResponseDTO;
import com.erp.service.ResidencePermitService;
import com.erp.service.security.annotation.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    @RequiresPermission(module = AppModule.IMMIGRATION, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
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
    @RequiresPermission(module = AppModule.IMMIGRATION, action = {AppAction.CREATE})
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
    @RequiresPermission(module = AppModule.IMMIGRATION, action = {AppAction.EDIT})
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
    // UPLOAD RESIDENCE PERMIT DOCUMENT (scan)
    // EDIT — attaching/replacing the residence-permit scan
    // ======================================================
    @RequiresPermission(module = AppModule.IMMIGRATION, action = {AppAction.EDIT})
    @PostMapping(value = "/{employeeId}/residence-permit/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResidencePermitResponseDTO> uploadDocument(
            @PathVariable("employeeId") Long employeeId,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(permitService.uploadDocument(employeeId, file));
    }

    // ======================================================
    // DELETE RESIDENCE PERMIT
    // DELETE — removing a residence permit record
    // ======================================================
    @RequiresPermission(module = AppModule.IMMIGRATION, action = {AppAction.DELETE})
    @DeleteMapping("/{employeeId}/residence-permit")
    public ResponseEntity<Void> delete(
            @PathVariable("employeeId") Long employeeId
    ) {
        permitService.deleteByEmployee(employeeId);
        return ResponseEntity.noContent().build();
    }
}