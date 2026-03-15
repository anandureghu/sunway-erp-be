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

    public boolean has(Authentication auth, HrModule module, HrAction action) {
        return hasAccess(auth, module, action);
    }

    public boolean hasAny(Authentication auth, HrModule module, HrAction... actions) {
        for (HrAction action : actions) {
            if (hasAccess(auth, module, action)) return true;
        }
        return false;
    }

    public boolean hasAccess(Authentication auth, HrModule module, HrAction action) {
        if (auth == null || !auth.isAuthenticated()) return false;

        Role securityRole = extractSecurityRole(auth);
        if (securityRole == null) return false;

        // ADMIN / SUPER_ADMIN bypass
        if (securityRole == Role.ADMIN || securityRole == Role.SUPER_ADMIN) return true;

        Long userId = getLoggedUserId(auth);
        RolePermission permission = null;

        // 1. Employee-specific override
        if (userId != null) {
            permission = repository
                    .findByEmployee_IdAndModule(userId, module)
                    .orElse(null);
        }

        // 2. Try companyRole first ("User", "HR Manager") — for when permissions are stored against companyRole
        if (permission == null) {
            String companyRole = extractCompanyRole(auth);
            if (companyRole != null) {
                permission = repository
                        .findByRoleAndModule(companyRole, module)
                        .orElse(null);
            }
        }

        // 3. Fallback to enum name ("USER", "HR") — for when permissions are stored against enum
        if (permission == null) {
            String enumRole = extractEnumRoleName(auth);
            if (enumRole != null) {
                permission = repository
                        .findByRoleAndModule(enumRole, module)
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

    private Role extractSecurityRole(Authentication auth) {
        if (auth.getPrincipal() instanceof CustomUserPrincipal user) {
            return user.getRole();
        }
        return null;
    }

    /**
     * Returns companyRole e.g. "User", "HR Manager" — checked first.
     */
    private String extractCompanyRole(Authentication auth) {
        if (auth.getPrincipal() instanceof CustomUserPrincipal user) {
            String cr = user.getCompanyRole();
            return (cr != null && !cr.isBlank()) ? cr : null;
        }
        return null;
    }

    /**
     * Returns enum name e.g. "USER", "HR" — fallback when no companyRole match.
     */
    private String extractEnumRoleName(Authentication auth) {
        if (auth.getPrincipal() instanceof CustomUserPrincipal user) {
            return user.getRole() != null ? user.getRole().name() : null;
        }
        return null;
    }

    public Long getLoggedUserId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal user) {
            return user.getId();
        }
        return null;
    }
}