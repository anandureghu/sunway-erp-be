package com.erp.controller.security;

import com.erp.domain.security.Role;
import com.erp.domain.security.RolePermission;
import com.erp.dto.security.ModulePermissionDTO;
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

    // ✅ NEW - Any logged-in user can fetch their OWN permissions
    // No @PreAuthorize - just needs to be authenticated
    @GetMapping("/my-permissions")
    public List<RolePermission> getMyPermissions(Authentication authentication) {
        String roleName = authentication.getAuthorities()
                .stream().findFirst().orElseThrow()
                .getAuthority().replace("ROLE_", "");
        Role role = Role.valueOf(roleName);
        return service.getByRole(role);
    }

    // ⬇️ Keep all existing endpoints unchanged ⬇️

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).VIEW_ALL
        )
    """)
    @GetMapping("/{role}")
    public List<RolePermission> getByRole(@PathVariable("role") String role) {
        Role roleEnum = Role.valueOf(role.toUpperCase());
        return service.getByRole(roleEnum);
    }

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
            @RequestBody List<ModulePermissionDTO> dtos) {
        Role roleEnum = Role.valueOf(role.toUpperCase());
        service.assignPermissions(roleEnum, dtos);
    }

    @PreAuthorize("""
        @permissionChecker.has(
            authentication,
            T(com.erp.domain.security.HrModule).HR_SETTINGS,
            T(com.erp.domain.security.HrAction).DELETE
        )
    """)
    @DeleteMapping("/{role}")
    public void removeAll(@PathVariable("role") String role) {
        System.out.println("DELETE ENDPOINT HIT FOR ROLE: " + role);
        Role roleEnum = Role.valueOf(role.toUpperCase());
        service.removeAll(roleEnum);
    }
}