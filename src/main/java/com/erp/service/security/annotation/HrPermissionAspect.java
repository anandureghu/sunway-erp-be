package com.erp.service.security.annotation;

import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
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
public class HrPermissionAspect {

    private final PermissionCheckService permissionCheckService;

    @Before("@annotation(hrPermission)")
    public void checkPermission(HrPermission hrPermission) {

        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("User not authenticated");
        }

        HrModule module = hrPermission.module();
        HrAction[] actions = hrPermission.action();

        boolean allowed = false;

        for (HrAction action : actions) {

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