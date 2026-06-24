package com.erp.service.security;

import com.erp.domain.security.CompanyRolePermission;
import com.erp.domain.security.EmployeePermission;
import com.erp.domain.security.EnumRolePermission;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.domain.security.Role;
import com.erp.repo.security.CompanyRolePermissionRepository;
import com.erp.repo.security.EmployeePermissionRepository;
import com.erp.repo.security.EnumRolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service("permissionChecker")
@RequiredArgsConstructor
@Slf4j
public class PermissionCheckService {

    private final EmployeePermissionRepository employeePermissionRepository;
    private final CompanyRolePermissionRepository companyRolePermissionRepository;
    private final EnumRolePermissionRepository enumRolePermissionRepository;

    // ======================================================
    // ENTRY METHODS
    // ======================================================

    public boolean has(Authentication auth, AppModule module, AppAction action) {
        return hasAccess(auth, module, action);
    }

    public boolean hasAny(Authentication auth, AppModule module, AppAction... actions) {
        if (actions == null || actions.length == 0) return false;

        for (AppAction action : actions) {
            if (hasAccess(auth, module, action)) return true;
        }
        return false;
    }

    // ======================================================
    // CORE LOGIC (🔥 FIXED)
    // ======================================================

    public boolean hasAccess(Authentication auth, AppModule module, AppAction action) {

        if (auth == null || !auth.isAuthenticated()) {
            log.debug("Access denied: unauthenticated");
            return false;
        }

        CustomUserPrincipal user = extractUser(auth);
        if (user == null) {
            log.warn("Access denied: invalid principal");
            return false;
        }

        if (module == null || action == null) {
            log.warn("Access denied: module/action null");
            return false;
        }

        // 1. ADMIN / SUPER_ADMIN bypass.
        // TODO: narrow ADMIN to per-module grants once enum_role_permissions
        // is seeded. Removing the bypass today locks every ADMIN out because
        // the lookup tables (enum_role_permissions, company_role_permissions)
        // have no rows for the ADMIN role.
        if (isAdmin(user)) {
            log.debug("Admin bypass granted for user={}", user.getUsername());
            return true;
        }

        // 🔥 2. EMPLOYEE PERMISSION
        Optional<EmployeePermission> employeePermission = resolveEmployeePermission(user, module);
        if (employeePermission.isPresent()) {
            boolean allowed = evaluate(employeePermission.get(), action);
            log.debug("Employee permission: user={} allowed={}", user.getUsername(), allowed);
            return allowed;
        }

        // 🔥 3. COMPANY ROLE PERMISSION (FIXED NULL SAFE)
        Optional<CompanyRolePermission> companyRolePermission = resolveCompanyRolePermission(user, module);
        if (companyRolePermission.isPresent()) {
            boolean allowed = evaluate(companyRolePermission.get(), action);
            log.debug("CompanyRole permission: user={} allowed={}", user.getUsername(), allowed);
            return allowed;
        }

        // 🔥 4. ENUM ROLE PERMISSION (fallback)
        Optional<EnumRolePermission> enumRolePermission = resolveEnumRolePermission(user, module);
        if (enumRolePermission.isPresent()) {
            boolean allowed = evaluate(enumRolePermission.get(), action);
            log.debug("EnumRole permission: user={} allowed={}", user.getUsername(), allowed);
            return allowed;
        }

        // ❌ FINAL DENY
        log.warn("Access denied: no permission found for user={} module={} action={}",
                user.getUsername(), module, action);

        return false;
    }

    // ======================================================
    // HELPERS
    // ======================================================

    private CustomUserPrincipal extractUser(Authentication auth) {
        if (auth == null) return null;

        Object principal = auth.getPrincipal();

        if (principal instanceof CustomUserPrincipal user) {
            return user;
        }

        return null;
    }

    private boolean isAdmin(CustomUserPrincipal user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN;
    }

    // ======================================================
    // PERMISSION RESOLVERS (🔥 FIXED)
    // ======================================================

    private Optional<EmployeePermission> resolveEmployeePermission(CustomUserPrincipal user, AppModule module) {

        if (user.getEmployeeId() == null || user.getEmployeeId() <= 0) {
            return Optional.empty();
        }

        Optional<EmployeePermission> permission = employeePermissionRepository
                .findByEmployeeIdAndModule(user.getEmployeeId(), module);

        if (permission.isEmpty()) {
            return Optional.empty();
        }

        EmployeePermission p = permission.get();

        // Disabled rule: saved but not enforced — fall through to the next layer.
        if (!p.isActive()) {
            return Optional.empty();
        }

        // Tenant guard: the Employee this permission belongs to must belong
        // to the caller's company. Stops a stale employeeId in a JWT from
        // granting access via another tenant's permission row.
        Long permCompanyId = p.getEmployee() != null && p.getEmployee().getCompany() != null
                ? p.getEmployee().getCompany().getId()
                : null;
        if (permCompanyId == null
                || user.getCompanyId() == null
                || !permCompanyId.equals(user.getCompanyId())) {
            log.warn("Cross-tenant EmployeePermission rejected: permissionId={} permCompanyId={} userCompanyId={}",
                    p.getId(), permCompanyId, user.getCompanyId());
            return Optional.empty();
        }

        return permission;
    }

    private Optional<CompanyRolePermission> resolveCompanyRolePermission(CustomUserPrincipal user, AppModule module) {

        if (user.getCompanyRoleId() == null || user.getCompanyRoleId() <= 0) {
            return Optional.empty();
        }

        Optional<CompanyRolePermission> permission =
                companyRolePermissionRepository
                        .findByCompanyRoleIdAndModule(user.getCompanyRoleId(), module);

        if (permission.isEmpty()) {
            return Optional.empty();
        }

        CompanyRolePermission p = permission.get();

        // Disabled rule: saved but not enforced — fall through to the next layer.
        if (!p.isActive()) {
            return Optional.empty();
        }

        if (p.getCompanyRole() == null) {
            log.warn("CompanyRole is NULL for permissionId={}", p.getId());
            return Optional.empty();
        }

        // Tenant guard: the CompanyRole this permission belongs to must
        // belong to the caller's company. Stops a stale companyRoleId in a
        // JWT from granting access via another tenant's permission row.
        Long roleCompanyId = p.getCompanyRole().getCompany() != null
                ? p.getCompanyRole().getCompany().getId()
                : null;
        if (roleCompanyId == null
                || user.getCompanyId() == null
                || !roleCompanyId.equals(user.getCompanyId())) {
            log.warn("Cross-tenant CompanyRolePermission rejected: permissionId={} roleCompanyId={} userCompanyId={}",
                    p.getId(), roleCompanyId, user.getCompanyId());
            return Optional.empty();
        }

        if (!Boolean.TRUE.equals(p.getCompanyRole().getActive())) {
            log.warn("CompanyRole inactive for permissionId={}", p.getId());
            return Optional.empty();
        }

        return permission;
    }

    private Optional<EnumRolePermission> resolveEnumRolePermission(CustomUserPrincipal user, AppModule module) {

        if (user.getRole() == null) {
            return Optional.empty();
        }

        return enumRolePermissionRepository
                .findByRoleAndModule(user.getRole(), module);
    }

    // ======================================================
    // EVALUATION
    // ======================================================

    private boolean evaluate(EmployeePermission p, AppAction action) {
        return evaluate(
                p.isViewOwn(), p.isViewAll(),
                p.isCreateOwn(), p.isCreateAll(),
                p.isEditOwn(), p.isEditAll(),
                p.isDeleteOwn(), p.isDeleteAll(),
                p.isApprove(), action);
    }

    private boolean evaluate(CompanyRolePermission p, AppAction action) {
        return evaluate(
                p.isViewOwn(), p.isViewAll(),
                p.isCreateOwn(), p.isCreateAll(),
                p.isEditOwn(), p.isEditAll(),
                p.isDeleteOwn(), p.isDeleteAll(),
                p.isApprove(), action);
    }

    private boolean evaluate(EnumRolePermission p, AppAction action) {
        return evaluate(
                p.isViewOwn(), p.isViewAll(),
                p.isCreateOwn(), p.isCreateAll(),
                p.isEditOwn(), p.isEditAll(),
                p.isDeleteOwn(), p.isDeleteAll(),
                p.isApprove(), action);
    }

    private boolean evaluate(
            boolean viewOwn,
            boolean viewAll,
            boolean createOwn,
            boolean createAll,
            boolean editOwn,
            boolean editAll,
            boolean deleteOwn,
            boolean deleteAll,
            boolean approve,
            AppAction action
    ) {
        return switch (action) {
            case VIEW_OWN -> viewOwn;
            case VIEW_ALL -> viewAll;
            // Coarse base actions: granted when either own or all is set.
            case CREATE -> createOwn || createAll;
            case EDIT -> editOwn || editAll;
            case DELETE -> deleteOwn || deleteAll;
            // Scoped variants.
            case CREATE_OWN -> createOwn;
            case CREATE_ALL -> createAll;
            case EDIT_OWN -> editOwn;
            case EDIT_ALL -> editAll;
            case DELETE_OWN -> deleteOwn;
            case DELETE_ALL -> deleteAll;
            case APPROVE -> approve;
        };
    }

    // ======================================================
    // OPTIONAL HELPERS (UNCHANGED)
    // ======================================================

    public Long getLoggedUserId(Authentication auth) {
        CustomUserPrincipal user = extractUser(auth);
        return user != null ? user.getId() : null;
    }

    public Long getLoggedEmployeeId(Authentication auth) {
        CustomUserPrincipal user = extractUser(auth);
        return user != null ? user.getEmployeeId() : null;
    }

    public Long getLoggedCompanyRoleId(Authentication auth) {
        CustomUserPrincipal user = extractUser(auth);
        return user != null ? user.getCompanyRoleId() : null;
    }

    public Role getLoggedRole(Authentication auth) {
        CustomUserPrincipal user = extractUser(auth);
        return user != null ? user.getRole() : null;
    }
}