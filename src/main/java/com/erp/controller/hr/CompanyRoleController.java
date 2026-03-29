package com.erp.controller.hr;

import com.erp.dto.hr.CompanyRoleDTO;
import com.erp.service.hr.CompanyRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("""
    @permissionChecker.has(
        authentication,
        T(com.erp.domain.security.HrModule).HR_SETTINGS,
        T(com.erp.domain.security.HrAction).VIEW_ALL
    )
""")
public class CompanyRoleController {

    private final CompanyRoleService roleService;

    // ======================================================
    // LIST ROLES
    // ======================================================

    @GetMapping
    public ResponseEntity<List<CompanyRoleDTO.Response>> list(
            @RequestParam("companyId") Long companyId
    ) {
        return ResponseEntity.ok(roleService.listByCompany(companyId));
    }

    // ======================================================
    // LIST ACTIVE ROLES
    // ======================================================

    @GetMapping("/active")
    public ResponseEntity<List<CompanyRoleDTO.Response>> listActive(
            @RequestParam("companyId") Long companyId
    ) {
        return ResponseEntity.ok(roleService.listActiveByCompany(companyId));
    }

    // ======================================================
    // GET ROLE BY ID
    // ======================================================

    @GetMapping("/{id}")
    public ResponseEntity<CompanyRoleDTO.Response> getById(
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(roleService.getById(id));
    }

    // ======================================================
    // CREATE ROLE
    // ======================================================

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).EDIT
        )
    """)
    @PostMapping
    public ResponseEntity<CompanyRoleDTO.Response> create(
            @Valid @RequestBody CompanyRoleDTO.Request dto
    ) {
        return ResponseEntity.ok(roleService.create(dto));
    }

    // ======================================================
    // UPDATE ROLE
    // ======================================================

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).EDIT
        )
    """)
    @PutMapping("/{id}")
    public ResponseEntity<CompanyRoleDTO.Response> update(
            @PathVariable("id") Long id,
            @Valid @RequestBody CompanyRoleDTO.Request dto
    ) {
        return ResponseEntity.ok(roleService.update(id, dto));
    }

    // ======================================================
    // TOGGLE ACTIVE
    // ======================================================

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).EDIT
        )
    """)
    @PutMapping("/{id}/toggle")
    public ResponseEntity<CompanyRoleDTO.Response> toggle(
            @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(roleService.toggleActive(id));
    }

    // ======================================================
    // DELETE ROLE
    // ======================================================

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).DELETE
        )
    """)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") Long id
    ) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}