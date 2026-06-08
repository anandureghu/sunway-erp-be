package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.contact.EmployeeAddressRequestDTO;
import com.erp.dto.contact.EmployeeAddressResponseDTO;
import com.erp.service.EmployeeAddressService;
import com.erp.service.security.annotation.RequiresPermission;
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
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping("/employees/{employeeId}/addresses")
    public ResponseEntity<List<EmployeeAddressResponseDTO>> getAll(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(service.getAddresses(employeeId));
    }

    // ======================================================
    // ADD ADDRESS
    // EDIT — adding an address is an edit operation
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.EDIT})
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
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.EDIT})
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
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.DELETE})
    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> delete(
            @PathVariable("addressId") Long addressId) {

        service.deleteAddress(addressId);
        return ResponseEntity.noContent().build();
    }
}