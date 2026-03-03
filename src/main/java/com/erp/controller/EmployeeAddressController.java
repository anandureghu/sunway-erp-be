package com.erp.controller;

import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.contact.EmployeeAddressRequestDTO;
import com.erp.dto.contact.EmployeeAddressResponseDTO;
import com.erp.service.EmployeeAddressService;
import com.erp.service.security.annotation.HrPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class EmployeeAddressController {

    private final EmployeeAddressService service;

    // ======================================================
    // GET ALL ADDRESSES
    // VIEW_OWN — user can view their own addresses
    // VIEW_ALL — admin/HR can view anyone's addresses
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.VIEW_OWN, HrAction.VIEW_ALL})
    @GetMapping("/employees/{employeeId}/addresses")
    public ResponseEntity<List<EmployeeAddressResponseDTO>> getAll(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(service.getAddresses(employeeId));
    }

    // ======================================================
    // ADD ADDRESS
    // EDIT — adding an address is an edit operation
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.EDIT})
    @PostMapping("/employees/{employeeId}/addresses")
    public ResponseEntity<EmployeeAddressResponseDTO> add(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody EmployeeAddressRequestDTO dto) {

        return ResponseEntity.ok(service.addAddress(employeeId, dto));
    }

    // ======================================================
    // UPDATE ADDRESS
    // EDIT — updating an address is an edit operation
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.EDIT})
    @PutMapping("/addresses/{addressId}")
    public ResponseEntity<EmployeeAddressResponseDTO> update(
            @PathVariable("addressId") Long addressId,
            @RequestBody EmployeeAddressRequestDTO dto) {

        return ResponseEntity.ok(service.updateAddress(addressId, dto));
    }

    // ======================================================
    // DELETE ADDRESS
    // DELETE — removing an address requires delete permission
    // ======================================================
    @HrPermission(module = HrModule.EMPLOYEE_PROFILE, action = {HrAction.DELETE})
    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> delete(
            @PathVariable("addressId") Long addressId) {

        service.deleteAddress(addressId);
        return ResponseEntity.noContent().build();
    }
}