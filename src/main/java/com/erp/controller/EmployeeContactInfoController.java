package com.erp.controller;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.contact.EmployeeContactInfoRequestDTO;
import com.erp.dto.contact.EmployeeContactInfoResponseDTO;
import com.erp.service.EmployeeContactInfoService;
import com.erp.service.security.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/employees/{employeeId}/contact-info")
public class EmployeeContactInfoController {

    private final EmployeeContactInfoService service;

    // ======================================================
    // GET CONTACT INFO
    // VIEW_OWN — user can view their own contact info
    // VIEW_ALL — admin/HR can view anyone's contact info
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.VIEW_OWN, AppAction.VIEW_ALL})
    @GetMapping
    public ResponseEntity<EmployeeContactInfoResponseDTO> get(
            @PathVariable("employeeId") Long employeeId) {

        return ResponseEntity.ok(service.getContactInfo(employeeId));
    }

    // ======================================================
    // SAVE OR UPDATE CONTACT INFO
    // EDIT — user with edit permission can update contact info
    // ======================================================
    @RequiresPermission(module = AppModule.EMPLOYEE_PROFILE, action = {AppAction.EDIT})
    @PutMapping
    public ResponseEntity<EmployeeContactInfoResponseDTO> save(
            @PathVariable("employeeId") Long employeeId,
            @RequestBody EmployeeContactInfoRequestDTO dto) {

        return ResponseEntity.ok(
                service.saveOrUpdateContactInfo(employeeId, dto)
        );
    }
}