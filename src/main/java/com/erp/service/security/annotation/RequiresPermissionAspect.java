package com.erp.service.security.annotation;

import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.service.security.PermissionCheckService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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

        boolean allowed = false;

        for (AppAction action : actions) {
            if (permissionCheckService.hasAccess(auth, module, action)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            throw new AccessDeniedException(
                    "Access denied: missing required permission on [" + module + "]"
            );
        }
    }
}
