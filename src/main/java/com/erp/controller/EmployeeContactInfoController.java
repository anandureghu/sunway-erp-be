package com.erp.controller;

import com.erp.dto.contact.EmployeeContactInfoRequestDTO;
import com.erp.dto.contact.EmployeeContactInfoResponseDTO;
import com.erp.service.EmployeeAddressService;
import com.erp.service.EmployeeContactInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/employees/{employeeId}/contact-info")
@RequiredArgsConstructor
public class EmployeeContactInfoController {

    private final EmployeeAddressService service;

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
