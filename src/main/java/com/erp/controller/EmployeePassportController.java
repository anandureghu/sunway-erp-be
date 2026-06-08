package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.immigration.PassportRequestDTO;
import com.erp.dto.immigration.PassportResponseDTO;
import com.erp.service.PassportService;
import com.erp.service.security.annotation.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeePassportController {

    private final PassportService passportService;

    // ======================================================
    // GET PASSPORT
    // VIEW_OWN — user views their own passport
    // VIEW_ALL — admin/HR views anyone's passport
    // ======================================================
    @RequiresPermission(module = AppModule.IMMIGRATION, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping("/{employeeId}/passport")
    public ResponseEntity<PassportResponseDTO> getPassport(
            @PathVariable("employeeId") Long employeeId
    ) {
        return ResponseEntity.ok(passportService.getByEmployee(employeeId));
    }

    // ======================================================
    // CREATE PASSPORT
    // CREATE — adding a new passport record
    // ======================================================
    @RequiresPermission(module = AppModule.IMMIGRATION, action = {AppAction.CREATE})
    @PostMapping("/{employeeId}/passport")
    public ResponseEntity<PassportResponseDTO> createPassport(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody PassportRequestDTO dto
    ) {
        dto.setEmployeeId(employeeId);
        return ResponseEntity.ok(passportService.create(dto));
    }

    // ======================================================
    // UPDATE PASSPORT
    // EDIT — updating an existing passport record
    // ======================================================
    @RequiresPermission(module = AppModule.IMMIGRATION, action = {AppAction.EDIT})
    @PutMapping("/{employeeId}/passport")
    public ResponseEntity<PassportResponseDTO> updatePassport(
            @PathVariable("employeeId") Long employeeId,
            @Valid @RequestBody PassportRequestDTO dto
    ) {
        dto.setEmployeeId(employeeId);
        return ResponseEntity.ok(passportService.update(dto));
    }

    // ======================================================
    // DELETE PASSPORT
    // DELETE — removing a passport record
    // ======================================================
    @RequiresPermission(module = AppModule.IMMIGRATION, action = {AppAction.DELETE})
    @DeleteMapping("/{employeeId}/passport")
    public ResponseEntity<Void> deletePassport(
            @PathVariable("employeeId") Long employeeId
    ) {
        passportService.deleteByEmployee(employeeId);
        return ResponseEntity.noContent().build();
    }
}