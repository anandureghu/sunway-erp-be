package com.erp.controller.security;

import com.erp.domain.security.RolePermission;
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

    // ======================================================
    // MY PERMISSIONS
    // ======================================================

    @PreAuthorize("""
        @permissionChecker.hasAny(
            authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).VIEW_OWN,
            T(com.erp.domain.security.HrAction).VIEW_ALL
        )
    """)
    @GetMapping("/my-permissions")
    public List<RolePermission> getMyPermissions(Authentication authentication) {

        if (!(authentication.getPrincipal() instanceof CustomUserPrincipal user)) {
            throw new RuntimeException("Invalid principal");
        }

        Long employeeId = permissionCheckService.getLoggedUserId(authentication);

        // Use companyRole first
        String roleName = normalizeRole(user.getCompanyRole());

        if (roleName == null) {
            roleName = user.getRole().name();
        }

        return service.getPermissionsForUser(employeeId, roleName);
    }

    // ======================================================
    // GET PERMISSIONS BY ROLE
    // ======================================================

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).VIEW_ALL
        )
    """)
    @GetMapping("/{role}")
    public List<RolePermission> getPermissions(
            @PathVariable("role") String role
    ) {
        return service.getByRole(role);
    }

    // ======================================================
    // ASSIGN PERMISSIONS
    // ======================================================

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
            @RequestBody List<com.erp.dto.security.ModulePermissionDTO> dtos
    ) {
        service.assignPermissions(role, employeeId, dtos);
    }

    // ======================================================
    // DELETE PERMISSIONS
    // ======================================================

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

    // ======================================================
    // HELPER
    // ======================================================

    private String normalizeRole(String role) {
        return (role == null || role.isBlank()) ? null : role.trim();
    }
}