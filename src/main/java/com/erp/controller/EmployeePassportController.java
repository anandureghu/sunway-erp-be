package com.erp.controller;

import com.erp.dto.immigration.PassportRequestDTO;
import com.erp.dto.immigration.PassportResponseDTO;
import com.erp.service.PassportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeePassportController {

    private final PassportService passportService;

    @GetMapping("/{employeeId}/passport")
    public ResponseEntity<PassportResponseDTO> getPassport(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(passportService.getByEmployee(employeeId));
    }

    @PostMapping("/{employeeId}/passport")
    public ResponseEntity<PassportResponseDTO> createPassport(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody PassportRequestDTO dto
    ) {
        dto.setEmployeeId(employeeId);
        return ResponseEntity.ok(passportService.create(dto));
    }

    @PutMapping("/{employeeId}/passport")
    public ResponseEntity<PassportResponseDTO> updatePassport(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody PassportRequestDTO dto
    ) {
        dto.setEmployeeId(employeeId);
        return ResponseEntity.ok(passportService.update(dto));
    }

    @DeleteMapping("/{employeeId}/passport")
    public ResponseEntity<Void> deletePassport(
            @PathVariable("employeeId") Long employeeId
    ) {
        passportService.deleteByEmployee(employeeId);
        return ResponseEntity.noContent().build();
    }
}
