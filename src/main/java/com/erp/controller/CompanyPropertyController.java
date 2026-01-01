package com.erp.controller;

import com.erp.dto.property.CompanyPropertyRequestDTO;
import com.erp.dto.property.CompanyPropertyResponseDTO;
import com.erp.service.CompanyPropertyService;
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

    /* ================= GET ALL PROPERTIES ================= */
    @GetMapping
    public ResponseEntity<List<CompanyPropertyResponseDTO>> getProperties(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(
                service.getProperties(employeeId)
        );
    }

    /* ================= CREATE PROPERTY ================= */
    @PostMapping
    public ResponseEntity<Void> createProperty(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody CompanyPropertyRequestDTO dto) {

        service.createProperty(employeeId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /* ================= UPDATE PROPERTY ================= */
    @PutMapping("/{propertyId}")
    public ResponseEntity<Void> updateProperty(
            @PathVariable("employeeId") Long employeeId,
            @PathVariable("propertyId") Long propertyId,
            @RequestBody CompanyPropertyRequestDTO dto) {

        service.updateProperty(employeeId, propertyId, dto);
        return ResponseEntity.noContent().build();
    }
}
