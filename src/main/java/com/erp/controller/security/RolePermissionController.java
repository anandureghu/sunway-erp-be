package com.erp.controller.security;

import com.erp.domain.security.Role;
import com.erp.dto.security.ModulePermissionDTO;
import com.erp.dto.security.PermissionRecordDTO;
import com.erp.service.security.PermissionCheckService;
import com.erp.service.security.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/role-permissions")
@RequiredArgsConstructor
public class RolePermissionController {

    private final RolePermissionService service;
    private final PermissionCheckService permissionCheckService;

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/my-permissions")
    public List<PermissionRecordDTO> getMyPermissions(Authentication authentication) {
        Long employeeId = permissionCheckService.getLoggedEmployeeId(authentication);
        Long companyRoleId = permissionCheckService.getLoggedCompanyRoleId(authentication);
        Role role = permissionCheckService.getLoggedRole(authentication);

        if (role == null) {
            throw new IllegalStateException("Unable to resolve logged-in user role");
        }

        return service.getPermissionsForUser(employeeId, companyRoleId, role);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/enum-roles/{role}")
    public List<PermissionRecordDTO> getEnumRolePermissions(@PathVariable Role role) {
        return service.getEnumRolePermissions(role);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/enum-roles/{role}")
    public void assignEnumRolePermissions(
            @PathVariable Role role,
            @RequestBody List<ModulePermissionDTO> dtos
    ) {
        service.assignEnumRolePermissions(role, dtos);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/enum-roles/{role}")
    public void removeEnumRolePermissions(@PathVariable Role role) {
        service.removeEnumRolePermissions(role);
    }

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.AppModule).HR_SETTINGS,
            T(com.erp.domain.security.AppAction).VIEW_ALL
        )
    """)
    @GetMapping("/company-roles/{companyRoleId}")
    public List<PermissionRecordDTO> getCompanyRolePermissions(@PathVariable Long companyRoleId) {
        return service.getCompanyRolePermissions(companyRoleId);
    }

    // Write endpoints for permission grants are admin-only. An HR Manager
    // with HR_SETTINGS.EDIT must not be able to escalate by editing the
    // company-role / employee permission tables.
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/company-roles/{companyRoleId}")
    public void assignCompanyRolePermissions(
            @PathVariable Long companyRoleId,
            @RequestBody List<ModulePermissionDTO> dtos
    ) {
        service.assignCompanyRolePermissions(companyRoleId, dtos);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/company-roles/{companyRoleId}")
    public void removeCompanyRolePermissions(@PathVariable Long companyRoleId) {
        service.removeCompanyRolePermissions(companyRoleId);
    }

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.AppModule).HR_SETTINGS,
            T(com.erp.domain.security.AppAction).VIEW_ALL
        )
    """)
    @GetMapping("/employees/{employeeId}")
    public List<PermissionRecordDTO> getEmployeePermissions(@PathVariable Long employeeId) {
        return service.getEmployeePermissions(employeeId);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/employees/{employeeId}")
    public void assignEmployeePermissions(
            @PathVariable Long employeeId,
            @RequestBody List<ModulePermissionDTO> dtos
    ) {
        service.assignEmployeePermissions(employeeId, dtos);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/employees/{employeeId}")
    public void removeEmployeePermissions(@PathVariable Long employeeId) {
        service.removeEmployeePermissions(employeeId);
    }
}