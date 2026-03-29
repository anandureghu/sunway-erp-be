package com.erp.service.security;

import com.erp.domain.security.*;
import com.erp.repo.security.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("permissionChecker")
@RequiredArgsConstructor
@Slf4j
public class PermissionCheckService {

    private final RolePermissionRepository repository;

    // ======================================================
    // PUBLIC METHODS
    // ======================================================

    public boolean has(Authentication auth, HrModule module, HrAction action) {
        return hasAccess(auth, module, action);
    }

    public boolean hasAny(Authentication auth, HrModule module, HrAction... actions) {
        for (HrAction action : actions) {
            if (hasAccess(auth, module, action)) return true;
        }
        return false;
    }

    // ======================================================
    // CORE LOGIC (FIXED)
    // ======================================================

    public boolean hasAccess(Authentication auth, HrModule module, HrAction action) {

        if (auth == null || !auth.isAuthenticated()) {
            log.debug("Permission denied: unauthenticated");
            return false;
        }

        if (!(auth.getPrincipal() instanceof CustomUserPrincipal user)) {
            log.warn("Permission denied: invalid principal");
            return false;
        }

        // ✅ ADMIN / SUPER_ADMIN bypass
        if (isAdmin(user)) {
            log.debug("Admin bypass granted for user {}", user.getUsername());
            return true;
        }

        Long userId = user.getId();

        // 🔥 USE EFFECTIVE ROLE (CRITICAL FIX)
        String role = normalizeRole(user.getEffectiveRole());

        RolePermission permission = null;

        // ======================================================
        // 1. Employee override (highest priority)
        // ======================================================
        if (userId != null) {
            Optional<RolePermission> empPerm =
                    repository.findByEmployee_IdAndModule(userId, module);

            if (empPerm.isPresent()) {
                permission = empPerm.get();
                log.debug("Using EMPLOYEE override for user {} module {}", userId, module);
            }
        }

        // ======================================================
        // 2. Role-based permission (companyRole or enum fallback)
        // ======================================================
        if (permission == null && role != null) {

            Optional<RolePermission> rolePerm =
                    repository.findByRoleIgnoreCaseAndModule(role, module);

            if (rolePerm.isPresent()) {
                permission = rolePerm.get();
                log.debug("Using ROLE '{}' for module {}", role, module);
            } else {
                log.warn("No permission found for role='{}' module='{}'", role, module);
                return false;
            }
        }

        // ======================================================
        // FINAL CHECK
        // ======================================================
        if (permission == null) {
            log.warn("Permission NOT FOUND for user={}, module={}",
                    user.getUsername(), module);
            return false;
        }

        boolean allowed = evaluate(permission, action);

        log.debug("Permission check: user={}, role={}, module={}, action={}, allowed={}",
                user.getUsername(), role, module, action, allowed);

        return allowed;
    }

    // ======================================================
    // HELPERS
    // ======================================================

    private boolean isAdmin(CustomUserPrincipal user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN;
    }

    private boolean evaluate(RolePermission permission, HrAction action) {
        return switch (action) {
            case VIEW_OWN -> permission.isViewOwn();
            case VIEW_ALL -> permission.isViewAll();
            case CREATE   -> permission.isCreatePermission();
            case EDIT     -> permission.isEditPermission();
            case DELETE   -> permission.isDeletePermission();
            case APPROVE  -> permission.isApprove();
        };
    }

    private String normalizeRole(String role) {
        return (role == null || role.isBlank())
                ? null
                : role.trim().toUpperCase();
    }

    public Long getLoggedUserId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal user) {
            return user.getId();
        }
        return null;
    }
}