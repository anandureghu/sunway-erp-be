package com.erp.controller;

import com.erp.dto.contact.EmployeeAddressRequestDTO;
import com.erp.dto.contact.EmployeeAddressResponseDTO;
import com.erp.dto.contact.EmployeeContactInfoRequestDTO;
import com.erp.dto.contact.EmployeeContactInfoResponseDTO;
import com.erp.service.EmployeeAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EmployeeAddressController {

    private final EmployeeAddressService service;

    @GetMapping("/api/employees/{employeeId}/addresses")
    public ResponseEntity<List<EmployeeAddressResponseDTO>> getAll(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(service.getAddresses(employeeId));
    }

    @PostMapping("/api/employees/{employeeId}/addresses")
    public ResponseEntity<EmployeeAddressResponseDTO> add(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody EmployeeAddressRequestDTO dto) {

        return ResponseEntity.ok(service.addAddress(employeeId, dto));
    }

    @PutMapping
    public ResponseEntity<EmployeeContactInfoResponseDTO> save(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody EmployeeContactInfoRequestDTO dto) {

        return ResponseEntity.ok(service.saveOrUpdateContactInfo(employeeId, dto));
    }


    @DeleteMapping("/api/addresses/{addressId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long addressId) {
        service.deleteAddress(addressId);
        return ResponseEntity.noContent().build();
    }
}
