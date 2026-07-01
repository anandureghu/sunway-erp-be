package com.erp.service.security.annotation;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.service.security.PermissionCheckService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
public class RequiresPermissionAspect {

    private final PermissionCheckService permissionCheckService;

    @Before("@annotation(requiresPermission)")
    public void checkPermission(RequiresPermission requiresPermission) {

        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("User not authenticated");
        }

        AppModule module = requiresPermission.module();
        AppAction[] actions = requiresPermission.action();

        // Ownership context from the request path: /employees/{employeeId}/...
        // When the endpoint isn't employee-scoped, an *_OWN grant behaves like a
        // plain capability (there's no per-record owner to compare against).
        String pathEmployeeId = currentPathVariable("employeeId");
        boolean hasEmployeeScope = pathEmployeeId != null;
        Long callerEmployeeId = permissionCheckService.getLoggedEmployeeId(auth);
        boolean isOwn = hasEmployeeScope
                && callerEmployeeId != null
                && pathEmployeeId.equals(String.valueOf(callerEmployeeId));

        for (AppAction action : actions) {
            if (allows(auth, module, action, hasEmployeeScope, isOwn)) {
                return;
            }
        }

        throw new AccessDeniedException(
                "Access denied: missing required permission on [" + module + "]");
    }

    private boolean allows(Authentication auth, AppModule module, AppAction action,
                           boolean hasEmployeeScope, boolean isOwn) {
        if (action == AppAction.VIEW_ALL) {
            return has(auth, module, AppAction.VIEW_ALL);
        }
        if (action == AppAction.VIEW_OWN) {
            return has(auth, module, AppAction.VIEW_OWN)
                    && (!hasEmployeeScope || isOwn);
        }
        if (action == AppAction.CREATE || action == AppAction.CREATE_OWN
                || action == AppAction.CREATE_ALL) {
            return scopedWrite(auth, module, AppAction.CREATE_ALL, AppAction.CREATE_OWN,
                    hasEmployeeScope, isOwn);
        }
        if (action == AppAction.EDIT || action == AppAction.EDIT_OWN
                || action == AppAction.EDIT_ALL) {
            return scopedWrite(auth, module, AppAction.EDIT_ALL, AppAction.EDIT_OWN,
                    hasEmployeeScope, isOwn);
        }
        if (action == AppAction.DELETE || action == AppAction.DELETE_OWN
                || action == AppAction.DELETE_ALL) {
            return scopedWrite(auth, module, AppAction.DELETE_ALL, AppAction.DELETE_OWN,
                    hasEmployeeScope, isOwn);
        }
        if (action == AppAction.APPROVE) {
            return has(auth, module, AppAction.APPROVE);
        }
        return false;
    }

    private boolean scopedWrite(Authentication auth, AppModule module,
                                AppAction allAction, AppAction ownAction,
                                boolean hasEmployeeScope, boolean isOwn) {
        if (has(auth, module, allAction)) {
            return true;
        }
        // Own grant: requires the targeted record to be the caller's own.
        if (has(auth, module, ownAction)) {
            return !hasEmployeeScope || isOwn;
        }
        return false;
    }

    private boolean has(Authentication auth, AppModule module, AppAction action) {
        return permissionCheckService.hasAccess(auth, module, action);
    }

    @SuppressWarnings("unchecked")
    private String currentPathVariable(String name) {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> map) {
            Object value = ((Map<String, Object>) map).get(name);
            return value != null ? String.valueOf(value) : null;
        }
        return null;
    }
}
