package com.erp.controller;

import com.erp.dto.contact.EmployeeContactInfoRequestDTO;
import com.erp.dto.contact.EmployeeContactInfoResponseDTO;
import com.erp.service.EmployeeContactInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/employees/{employeeId}/contact-info")
public class EmployeeContactInfoController {

    private final EmployeeContactInfoService service;

    @GetMapping
    public ResponseEntity<EmployeeContactInfoResponseDTO> get(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(service.getContactInfo(employeeId));
    }

    @PutMapping
    public ResponseEntity<EmployeeContactInfoResponseDTO> save(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody EmployeeContactInfoRequestDTO dto) {

        return ResponseEntity.ok(
                service.saveOrUpdateContactInfo(employeeId, dto)
        );
    }
}
