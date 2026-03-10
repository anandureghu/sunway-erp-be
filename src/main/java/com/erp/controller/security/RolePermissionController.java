package com.erp.controller.security;

import com.erp.domain.security.RolePermission;
import com.erp.dto.security.ModulePermissionDTO;
import com.erp.service.security.CustomUserPrincipal;
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

    /* ── My Permissions ──────────────────────────────────────────────────────── */

    @GetMapping("/my-permissions")
    public List<RolePermission> getMyPermissions(Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof CustomUserPrincipal user)) {
            throw new RuntimeException("Unable to resolve user principal");
        }

        Long employeeId = permissionCheckService.getLoggedUserId(authentication);

        // Use companyRole if available, fall back to security role name
        String roleName = user.getCompanyRole() != null
                ? user.getCompanyRole()
                : user.getRole().name();

        return service.getPermissionsForUser(employeeId, roleName);
    }

    /* ── Get Permissions for a Role ─────────────────────────────────────────── */

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).VIEW_ALL
        )
    """)
    @GetMapping("/{role}")
    public List<RolePermission> getPermissions(
            @PathVariable("role") String role,
            @RequestParam(value = "employeeId", required = false) Long employeeId
    ) {
        // role is now a plain string — "External Auditor", "HR Manager", "USER" etc.
        // NO Role.valueOf() — that was the bug
        if (employeeId != null) {
            return service.getPermissionsForUser(employeeId, role);
        }
        return service.getByRole(role);
    }

    /* ── Assign Permissions ──────────────────────────────────────────────────── */

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).EDIT
        )
    """)
    @PostMapping("/{role}")
    public void assignPermissions(
            @PathVariable("role") String role,
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestBody List<ModulePermissionDTO> dtos
    ) {
        // role is a plain string — pass directly, no enum conversion
        service.assignPermissions(role, employeeId, dtos);
    }

    /* ── Remove All Permissions for a Role ───────────────────────────────────── */

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).DELETE
        )
    """)
    @DeleteMapping("/{role}")
    public void removeAll(@PathVariable("role") String role) {
        service.removeAll(role);
    }
}