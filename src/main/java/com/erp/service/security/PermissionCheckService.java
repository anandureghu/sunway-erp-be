package com.erp.service.security;

import com.erp.domain.security.*;
import com.erp.repo.security.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service("permissionChecker")
@RequiredArgsConstructor
public class PermissionCheckService {

    private final RolePermissionRepository repository;

    /* ================= BASIC CHECK ================= */

    public boolean has(Authentication auth,
                       HrModule module,
                       HrAction action) {
        return hasAccess(auth, module, action);
    }

    public boolean hasAny(Authentication auth,
                          HrModule module,
                          HrAction... actions) {

        for (HrAction action : actions) {
            if (hasAccess(auth, module, action)) {
                return true;
            }
        }
        return false;
    }

    /* ================= CORE PERMISSION LOGIC ================= */

    public boolean hasAccess(Authentication auth,
                             HrModule module,
                             HrAction action) {

        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        Role role = extractRole(auth);

        if (role == null) {
            return false;
        }

        // ADMIN bypass
        if (role == Role.ADMIN || role == Role.SUPER_ADMIN) {
            return true;
        }

        RolePermission permission =
                repository.findByRoleAndModule(role, module)
                        .orElse(null);

        if (permission == null) {
            return false;
        }

        return switch (action) {
            case VIEW_OWN  -> permission.isViewOwn();
            case VIEW_ALL  -> permission.isViewAll();
            case CREATE    -> permission.isCreatePermission();
            case EDIT      -> permission.isEditPermission();
            case DELETE    -> permission.isDeletePermission();
            case APPROVE   -> permission.isApprove();
        };
    }
    /* ================= ROLE EXTRACTION ================= */

    private Role extractRole(Authentication auth) {

        if (auth.getPrincipal() instanceof CustomUserPrincipal user) {
            return user.getRole();
        }

        return null;
    }

    /* ================= LOGGED USER SUPPORT ================= */

    public Long getLoggedUserId(Authentication auth) {

        if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal user) {
            return user.getId();
        }

        return null;
    }

}