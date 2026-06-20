package com.erp.security;

import com.erp.service.admin.AdminSystemLogService;
import com.erp.service.security.CustomUserPrincipal;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Populates MDC for admin DB logging (user, request path) on each HTTP request.
 */
@Component
public class AdminLogContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        try {
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            if (query != null && !query.isBlank()) {
                uri = uri + "?" + query;
            }
            MDC.put(AdminSystemLogService.MDC_REQUEST_URI, uri);
            MDC.put(AdminSystemLogService.MDC_REQUEST_METHOD, request.getMethod());

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal principal) {
                MDC.put(AdminSystemLogService.MDC_USER_ID, String.valueOf(principal.getUserId()));
                MDC.put(AdminSystemLogService.MDC_USER_USERNAME, principal.getUsername());
                if (principal.getCompanyId() != null) {
                    MDC.put(AdminSystemLogService.MDC_COMPANY_ID, String.valueOf(principal.getCompanyId()));
                }
            }

            Object details = auth != null ? auth.getDetails() : null;
            if (details instanceof Map<?, ?> map && map.get("claims") instanceof Claims claims) {
                Object email = claims.get("email");
                if (email != null) {
                    MDC.put(AdminSystemLogService.MDC_USER_EMAIL, String.valueOf(email));
                }
                if (MDC.get(AdminSystemLogService.MDC_USER_USERNAME) == null) {
                    Object username = claims.get("username");
                    if (username != null) {
                        MDC.put(AdminSystemLogService.MDC_USER_USERNAME, String.valueOf(username));
                    }
                }
                if (MDC.get(AdminSystemLogService.MDC_USER_ID) == null) {
                    Object userId = claims.get("userId");
                    if (userId != null) {
                        MDC.put(AdminSystemLogService.MDC_USER_ID, String.valueOf(userId));
                    }
                }
            }

            chain.doFilter(request, response);
        } finally {
            MDC.remove(AdminSystemLogService.MDC_REQUEST_URI);
            MDC.remove(AdminSystemLogService.MDC_REQUEST_METHOD);
            MDC.remove(AdminSystemLogService.MDC_USER_ID);
            MDC.remove(AdminSystemLogService.MDC_USER_EMAIL);
            MDC.remove(AdminSystemLogService.MDC_USER_USERNAME);
            MDC.remove(AdminSystemLogService.MDC_COMPANY_ID);
        }
    }
}
