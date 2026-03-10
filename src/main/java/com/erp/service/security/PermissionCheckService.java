package com.erp.service.security;

import com.erp.domain.security.*;
import com.erp.repo.security.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service("permissionChecker")
@RequiredArgsConstructor
public class PermissionCheckService {

    private final RolePermissionRepository repository;

    /* ── Basic checks ────────────────────────────────────────────────────────── */

    public boolean has(Authentication auth, HrModule module, HrAction action) {
        return hasAccess(auth, module, action);
    }

    public boolean hasAny(Authentication auth, HrModule module, HrAction... actions) {
        for (HrAction action : actions) {
            if (hasAccess(auth, module, action)) return true;
        }
        return false;
    }

    /* ── Core permission logic ───────────────────────────────────────────────── */

    public boolean hasAccess(Authentication auth, HrModule module, HrAction action) {

        if (auth == null || !auth.isAuthenticated()) return false;

        Role securityRole = extractSecurityRole(auth);
        if (securityRole == null) return false;

        // ADMIN / SUPER_ADMIN bypass — always full access
        if (securityRole == Role.ADMIN || securityRole == Role.SUPER_ADMIN) return true;

        Long userId = getLoggedUserId(auth);

        RolePermission permission = null;

        // 1. Employee-specific override takes priority
        if (userId != null) {
            permission = repository
                    .findByEmployee_IdAndModule(userId, module)
                    .orElse(null);
        }

        // 2. Fallback to role-wide permission
        //    Use companyRole ("HR Manager") — that's what's stored in role_permissions.role
        //    Fall back to enum name ("HR") for legacy/unassigned users
        if (permission == null) {
            String roleName = extractRoleName(auth);
            if (roleName != null) {
                permission = repository
                        .findByRoleAndModule(roleName, module)
                        .orElse(null);
            }
        }

        if (permission == null) return false;

        return switch (action) {
            case VIEW_OWN -> permission.isViewOwn();
            case VIEW_ALL -> permission.isViewAll();
            case CREATE   -> permission.isCreatePermission();
            case EDIT     -> permission.isEditPermission();
            case DELETE   -> permission.isDeletePermission();
            case APPROVE  -> permission.isApprove();
        };
    }

    /* ── Role extraction ─────────────────────────────────────────────────────── */

    /**
     * Returns the security Role enum — used ONLY for ADMIN/SUPER_ADMIN bypass check.
     */
    private Role extractSecurityRole(Authentication auth) {
        if (auth.getPrincipal() instanceof CustomUserPrincipal user) {
            return user.getRole();
        }
        return null;
    }

    /**
     * Returns the role name to use for permission table lookups.
     * Prefers companyRole ("HR Manager") over enum name ("HR").
     * This must match what's stored in role_permissions.role column.
     */
    private String extractRoleName(Authentication auth) {
        if (auth.getPrincipal() instanceof CustomUserPrincipal user) {
            // companyRole is the dynamic HR-managed role stored in role_permissions
            if (user.getCompanyRole() != null && !user.getCompanyRole().isBlank()) {
                return user.getCompanyRole();
            }
            // Fallback: enum name for users who haven't been assigned a companyRole yet
            return user.getRole() != null ? user.getRole().name() : null;
        }
        return null;
    }

    /* ── User ID helper ──────────────────────────────────────────────────────── */

    public Long getLoggedUserId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal user) {
            return user.getId();
        }
        return null;
    }
}