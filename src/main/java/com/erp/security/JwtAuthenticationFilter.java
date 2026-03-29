package com.erp.security;

import com.erp.domain.security.Role;
import com.erp.service.security.CustomUserPrincipal;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // ✅ Allow preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // ✅ Skip if already authenticated
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7).trim();

        try {
            Claims claims = jwtService.parse(token).getBody();

            // ─────────────────────────────────────────────
            // 🔹 Extract username
            // ─────────────────────────────────────────────
            String username = safeString(claims.get("username"));
            if (isBlank(username)) username = safeString(claims.get("preferred_username"));
            if (isBlank(username)) username = claims.getSubject();

            if (isBlank(username)) {
                log.warn("JWT missing username");
                chain.doFilter(request, response);
                return;
            }

            // ─────────────────────────────────────────────
            // 🔹 Extract authorities
            // ─────────────────────────────────────────────
            Collection<SimpleGrantedAuthority> authorities = extractAuthorities(claims);

            if (authorities.isEmpty()) {
                log.warn("JWT has no authorities for user '{}'", username);
                chain.doFilter(request, response);
                return;
            }

            // ─────────────────────────────────────────────
            // 🔹 Extract enum Role
            // ─────────────────────────────────────────────
            String roleName = authorities.stream()
                    .map(SimpleGrantedAuthority::getAuthority)
                    .findFirst()
                    .map(r -> r.replace("ROLE_", ""))
                    .orElse(null);

            if (roleName == null) {
                chain.doFilter(request, response);
                return;
            }

            Role role = Role.valueOf(roleName);

            // ─────────────────────────────────────────────
            // 🔹 Extract companyRole
            // ─────────────────────────────────────────────
            String companyRole = safeString(claims.get("companyRole"));

            // ─────────────────────────────────────────────
            // 🔹 Extract userId
            // ─────────────────────────────────────────────
            Long userId = parseLong(claims.get("userId"));

            // ─────────────────────────────────────────────
            // 🔹 Extract companyId ✅ NEW FIX
            // ─────────────────────────────────────────────
            Long companyId = parseLong(claims.get("companyId"));

            if (userId == null) {
                log.warn("JWT missing userId for '{}'", username);
            }

            if (companyId == null) {
                log.warn("JWT missing companyId for '{}'", username);
            }

            // ─────────────────────────────────────────────
            // 🔹 Build principal
            // ─────────────────────────────────────────────
            CustomUserPrincipal principal = new CustomUserPrincipal(
                    userId,
                    username,
                    "",
                    role,
                    companyRole,
                    companyId   // ✅ IMPORTANT
            );

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            Map<String, Object> details = new HashMap<>();
            details.put("web", new WebAuthenticationDetailsSource().buildDetails(request));
            details.put("claims", claims);

            auth.setDetails(details);

            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("JWT authenticated: user='{}', id={}, role={}, companyRole={}, companyId={}",
                    username, userId, role, companyRole, companyId);

        } catch (Exception ex) {
            log.debug("JWT parse failed: {}", ex.getMessage());
        }

        chain.doFilter(request, response);
    }

    // ─────────────────────────────────────────────
    // 🔧 Helpers
    // ─────────────────────────────────────────────

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safeString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Long parseLong(Object value) {
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Long l) return l;
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Collection<SimpleGrantedAuthority> extractAuthorities(Claims claims) {

        Set<String> roles = new LinkedHashSet<>();

        // role: "ADMIN"
        String singleRole = safeString(claims.get("role"));
        if (!isBlank(singleRole)) roles.add(singleRole);

        // roles: ["ADMIN", "HR"]
        Object arr = claims.get("roles");
        if (arr instanceof Collection<?> c) {
            c.forEach(x -> {
                String s = safeString(x);
                if (!isBlank(s)) roles.add(s);
            });
        }

        // authorities: ["ROLE_ADMIN"]
        Object auths = claims.get("authorities");
        if (auths instanceof Collection<?> c) {
            c.forEach(x -> {
                String s = safeString(x);
                if (!isBlank(s)) roles.add(s.replaceFirst("^ROLE_", ""));
            });
        }

        // scope: "admin user"
        String scope = safeString(claims.get("scope"));
        if (!isBlank(scope)) {
            for (String s : scope.split("\\s+")) {
                if (!isBlank(s)) roles.add(s.toUpperCase(Locale.ROOT));
            }
        }

        return roles.stream()
                .filter(r -> !isBlank(r))
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}