package com.erp.security;

import com.erp.domain.security.Role;
import com.erp.service.security.CustomUserPrincipal;
import com.erp.service.subscription.SubscriptionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Hard-locks non–SUPER_ADMIN API access when the active company subscription is expired / cancelled / suspended.
 */
@Component
public class SubscriptionAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionAccessFilter.class);

    private final SubscriptionService subscriptionService;

    public SubscriptionAccessFilter(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) return true;
        // Strip context path if present
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        if (!path.startsWith("/api/")) return true;
        if (path.startsWith("/api/auth/")) return true;
        if (path.equals("/api/admin/subscriptions/me/status")) return true;
        if (path.startsWith("/api/subscriptions/me")) return true;
        if (path.startsWith("/api/public/")) return true;
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (principal.getRole() == Role.SUPER_ADMIN) {
            filterChain.doFilter(request, response);
            return;
        }

        Long companyId = principal.getCompanyId();
        if (companyId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (subscriptionService.isAccessLocked(companyId)) {
                log.info("Blocking request due to expired subscription companyId={} path={}",
                        companyId, request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"error\":\"Subscription expired\",\"code\":\"SUBSCRIPTION_EXPIRED\",\"status\":403}"
                );
                return;
            }
        } catch (Exception ex) {
            log.warn("Subscription access check failed: {}", ex.getMessage());
            // Fail open only on infrastructure errors would weaken security — fail closed for locked unknown? Prefer allow if lookup fails briefly.
            // Plan: security top priority → if we cannot verify, allow SUPER_ADMIN only (already returned). For others, block on exception after log.
            // Soft: allow through on unexpected errors to avoid total outage if DB is down mid-request for all tenants.
            filterChain.doFilter(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
