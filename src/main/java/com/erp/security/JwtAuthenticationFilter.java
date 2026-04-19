package com.erp.security;

import com.erp.domain.security.Role;
import com.erp.service.security.CustomUserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
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
                log.warn(
                        "JWT has no role/authorities for user '{}' — use the access token from login, not the refresh token.",
                        username);
                chain.doFilter(request, response);
                return;
            }

            // ─────────────────────────────────────────────
            // 🔹 Map first matching authority to enum Role
            // ─────────────────────────────────────────────
            Role role = resolveRoleFromAuthorities(authorities);
            if (role == null) {
                log.warn(
                        "JWT authorities do not match any Role enum for user '{}': {}",
                        username,
                        authorities);
                chain.doFilter(request, response);
                return;
            }

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

        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired — login again or use refresh to obtain a new access token");
        } catch (Exception ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
        }

        chain.doFilter(request, response);
    }

    /**
     * Tries each granted authority until one matches {@link Role}; avoids 401 when the first
     * authority is not an enum constant (e.g. scope-derived strings) or has stray whitespace.
     */
    private static Role resolveRoleFromAuthorities(Collection<SimpleGrantedAuthority> authorities) {
        for (SimpleGrantedAuthority a : authorities) {
            String raw = a.getAuthority();
            if (raw == null) {
                continue;
            }
            String rn = raw.startsWith("ROLE_") ? raw.substring(5) : raw;
            rn = rn.trim();
            if (rn.isEmpty()) {
                continue;
            }
            try {
                return Role.valueOf(rn);
            } catch (IllegalArgumentException ignored) {
                // try next
            }
        }
        return null;
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
        if (!isBlank(singleRole)) {
            roles.add(singleRole.trim());
        }

        // roles: ["ADMIN", "HR"]
        Object arr = claims.get("roles");
        if (arr instanceof Collection<?> c) {
            c.forEach(x -> {
                String s = safeString(x);
                if (!isBlank(s)) {
                    roles.add(s.trim());
                }
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