package com.erp.controller;

import com.erp.dto.immigration.ResidencePermitRequestDTO;
import com.erp.dto.immigration.ResidencePermitResponseDTO;
import com.erp.service.ResidencePermitService;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeResidencePermitController {

    private final ResidencePermitService permitService;

    // ---------------- GET ----------------
    @GetMapping("/{employeeId}/residence-permit")
    public ResponseEntity<ResidencePermitResponseDTO> get(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(permitService.getByEmployee(employeeId));
    }

    // ---------------- CREATE ----------------
    @PostMapping("/{employeeId}/residence-permit")
    public ResponseEntity<ResidencePermitResponseDTO> create(
            @PathVariable("employeeId") Long employeeId,
            @Nonnull @Valid @RequestBody ResidencePermitRequestDTO dto
    ) {
        dto.setEmployeeId(employeeId);
        return ResponseEntity.ok(permitService.create(dto));
    }

    // ---------------- UPDATE ----------------
    @PutMapping("/{employeeId}/residence-permit")
    public ResponseEntity<ResidencePermitResponseDTO> update(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody ResidencePermitRequestDTO dto
    ) {
        dto.setEmployeeId(employeeId);
        return ResponseEntity.ok(permitService.update(dto));
    }

    // ---------------- DELETE ----------------
    @DeleteMapping("/{employeeId}/residence-permit")
    public ResponseEntity<Void> delete(
            @PathVariable("employeeId") Long employeeId
    ) {
        permitService.deleteByEmployee(employeeId);
        return ResponseEntity.noContent().build();
    }
}
