package com.erp.controller;

import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.property.CompanyPropertyRequestDTO;
import com.erp.dto.property.CompanyPropertyResponseDTO;
import com.erp.service.CompanyPropertyService;
import com.erp.service.security.annotation.HrPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees/{employeeId}/company-properties")
@RequiredArgsConstructor
public class CompanyPropertyController {

    private final CompanyPropertyService service;

    // ======================================================
    // GET ALL PROPERTIES
    // VIEW_OWN — user views their own assigned properties
    // VIEW_ALL — admin/HR views anyone's properties
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<List<CompanyPropertyResponseDTO>> getProperties(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(service.getProperties(employeeId));
    }

    // ======================================================
    // CREATE PROPERTY ASSIGNMENT
    // CREATE — assigning a new property to an employee
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.CREATE})
    @PostMapping
    public ResponseEntity<CompanyPropertyResponseDTO> createProperty(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody CompanyPropertyRequestDTO dto) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createProperty(employeeId, dto));
    }

    // ======================================================
    // UPDATE PROPERTY ASSIGNMENT
    // EDIT — updating an existing property assignment
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.EDIT})
    @PutMapping("/{propertyId}")
    public ResponseEntity<CompanyPropertyResponseDTO> updateProperty(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("propertyId") Long propertyId,
            @Valid @RequestBody CompanyPropertyRequestDTO dto) {

        return ResponseEntity.ok(
                service.updateProperty(employeeId, propertyId, dto));
    }

    // ======================================================
    // DELETE PROPERTY ASSIGNMENT
    // DELETE — removing a property assignment
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.DELETE})
    @DeleteMapping("/{propertyId}")
    public ResponseEntity<Void> deleteProperty(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("propertyId") Long propertyId) {

        service.deleteProperty(employeeId, propertyId);
        return ResponseEntity.noContent().build();
    }
}