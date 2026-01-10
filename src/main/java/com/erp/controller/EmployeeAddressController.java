package com.erp.controller;

import com.erp.dto.contact.EmployeeAddressRequestDTO;
import com.erp.dto.contact.EmployeeAddressResponseDTO;
import com.erp.service.EmployeeAddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequiredArgsConstructor
public class EmployeeAddressController {

    private final EmployeeAddressService service;

    // ================= GET ADDRESSES =================
    @GetMapping("/api/employees/{employeeId}/addresses")
    public ResponseEntity<List<EmployeeAddressResponseDTO>> getAll(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(service.getAddresses(employeeId));
    }

    // ================= ADD ADDRESS =================
    @PostMapping("/api/employees/{employeeId}/addresses")
    public ResponseEntity<EmployeeAddressResponseDTO> add(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody EmployeeAddressRequestDTO dto) {

        return ResponseEntity.ok(service.addAddress(employeeId, dto));
    }

    // ================= UPDATE ADDRESS =================
    @PutMapping("/api/addresses/{addressId}")
    public ResponseEntity<EmployeeAddressResponseDTO> update(
            @PathVariable("addressId") Long addressId,
            @RequestBody EmployeeAddressRequestDTO dto) {

        return ResponseEntity.ok(service.updateAddress(addressId, dto));
    }

    // ================= DELETE ADDRESS =================
    @DeleteMapping("/api/addresses/{addressId}")
    public ResponseEntity<Void> delete(
            @PathVariable("addressId") Long addressId) {

        service.deleteAddress(addressId);
        return ResponseEntity.noContent().build();
    }
}
