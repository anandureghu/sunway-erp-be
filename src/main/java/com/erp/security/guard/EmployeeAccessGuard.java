package com.erp.security.guard;

import com.erp.domain.Employee;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.security.context.AuthContext;
import com.erp.service.security.PermissionCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Centralized tenant + self-vs-all guard for endpoints that operate on a
 * specific Employee. Tenant boundary is always enforced (SUPER_ADMIN bypasses).
 * Action-level permission (CREATE/EDIT/DELETE) is expected to be enforced at
 * the controller via @PreAuthorize — this guard only enforces the *who can
 * touch which employee's row* question.
 */
@Component
@RequiredArgsConstructor
public class EmployeeAccessGuard {

    private final AuthContext authContext;
    private final PermissionCheckService permissionCheckService;

    /**
     * Caller may read data for this employee on the given module.
     * Passes if caller is SUPER_ADMIN, has VIEW_ALL on the module (same tenant),
     * or has VIEW_OWN and the employee is the caller's own record.
     */
    public void assertCanRead(Employee employee, AppModule module) {
        assertSameTenant(employee);
        if (isSuperAdmin()) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (permissionCheckService.hasAccess(auth, module, AppAction.VIEW_ALL)) {
            return;
        }
        if (permissionCheckService.hasAccess(auth, module, AppAction.VIEW_OWN)
                && isOwnEmployee(employee)) {
            return;
        }
        throw new AccessDeniedException("Access denied for this employee");
    }

    /**
     * Caller may write data for this employee on the given module.
     * Tenant must match (SUPER_ADMIN bypasses). The action-level grant
     * (CREATE/EDIT/DELETE) is enforced separately at the controller.
     */
    public void assertCanWrite(Employee employee, AppModule module) {
        assertSameTenant(employee);
    }

    /**
     * Self-service write: an employee may always act on their own row (e.g. punch
     * their own attendance) with no module grant. Acting on someone else's row
     * requires the module's *_ALL grant for the action. SUPER_ADMIN bypasses;
     * tenant is always enforced.
     */
    public void assertSelfServiceWrite(Employee employee, AppModule module, AppAction action) {
        assertSameTenant(employee);
        if (isSuperAdmin() || isOwnEmployee(employee)) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        AppAction all = allVariant(action);
        if (all != null && permissionCheckService.hasAccess(auth, module, all)) {
            return;
        }
        throw new AccessDeniedException(
                "Access denied: you can only record your own attendance");
    }

    /**
     * Ownership-aware write check for a specific action. Passes if the caller is
     * SUPER_ADMIN, has the *_ALL grant for the action, or has the *_OWN grant and
     * the employee is the caller's own record. APPROVE is treated as an "all"
     * action (you approve others' requests).
     */
    public void assertCanWrite(Employee employee, AppModule module, AppAction action) {
        assertSameTenant(employee);
        if (isSuperAdmin()) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (action == AppAction.APPROVE) {
            if (permissionCheckService.hasAccess(auth, module, AppAction.APPROVE)) {
                return;
            }
            throw new AccessDeniedException("Access denied: missing approve permission");
        }

        AppAction all = allVariant(action);
        AppAction own = ownVariant(action);
        if (all != null && permissionCheckService.hasAccess(auth, module, all)) {
            return;
        }
        if (own != null
                && permissionCheckService.hasAccess(auth, module, own)
                && isOwnEmployee(employee)) {
            return;
        }
        throw new AccessDeniedException(
                "Access denied: insufficient permission for this employee");
    }

    private AppAction allVariant(AppAction a) {
        return switch (a) {
            case CREATE, CREATE_OWN, CREATE_ALL -> AppAction.CREATE_ALL;
            case EDIT, EDIT_OWN, EDIT_ALL -> AppAction.EDIT_ALL;
            case DELETE, DELETE_OWN, DELETE_ALL -> AppAction.DELETE_ALL;
            default -> null;
        };
    }

    private AppAction ownVariant(AppAction a) {
        return switch (a) {
            case CREATE, CREATE_OWN, CREATE_ALL -> AppAction.CREATE_OWN;
            case EDIT, EDIT_OWN, EDIT_ALL -> AppAction.EDIT_OWN;
            case DELETE, DELETE_OWN, DELETE_ALL -> AppAction.DELETE_OWN;
            default -> null;
        };
    }

    private void assertSameTenant(Employee employee) {
        if (isSuperAdmin()) {
            return;
        }
        Long callerCompanyId = authContext.getCurrentCompanyId();
        Long empCompanyId = employee.getCompany() != null
                ? employee.getCompany().getId()
                : null;
        if (callerCompanyId == null
                || empCompanyId == null
                || !callerCompanyId.equals(empCompanyId)) {
            throw new AccessDeniedException("Cross-tenant access denied");
        }
    }

    private boolean isOwnEmployee(Employee employee) {
        Long callerUserId = authContext.getCurrentUserId();
        Long empUserId = employee.getUser() != null ? employee.getUser().getId() : null;
        return callerUserId != null && callerUserId.equals(empUserId);
    }

    private boolean isSuperAdmin() {
        String role = authContext.getCurrentUserRole();
        return "SUPER_ADMIN".equalsIgnoreCase(role);
    }
}
