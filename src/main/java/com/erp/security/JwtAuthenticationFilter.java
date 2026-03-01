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

        // Allow CORS preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // If already authenticated, skip
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

            // 🔹 Extract username
            String username = safeString(claims.get("username"));
            if (isBlank(username))
                username = safeString(claims.get("preferred_username"));
            if (isBlank(username))
                username = claims.getSubject();

            if (isBlank(username)) {
                log.warn("JWT missing username/sub claim");
                chain.doFilter(request, response);
                return;
            }

            // 🔹 Extract authorities
            Collection<SimpleGrantedAuthority> authorities =
                    extractAuthorities(claims);

            if (authorities.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }

            // 🔥 Extract role from authorities
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

            // 🔥 Create CustomUserPrincipal instead of Spring User
            CustomUserPrincipal principal =
                    new CustomUserPrincipal(
                            null,           // userId (optional if not in token)
                            username,
                            "",             // password not needed for JWT
                            role
                    );

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            authorities
                    );

            // Add details
            Map<String, Object> details = new HashMap<>();
            details.put("web",
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request));
            details.put("claims", claims);

            auth.setDetails(details);

            SecurityContextHolder.getContext()
                    .setAuthentication(auth);

            if (log.isDebugEnabled()) {
                log.debug("JWT authenticated '{}', role={}", username, role);
            }

        } catch (Exception ex) {
            log.debug("JWT parse/authorize failed: {}", ex.getMessage());
        }

        chain.doFilter(request, response);
    }

    /* ================= HELPERS ================= */

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safeString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Collection<SimpleGrantedAuthority>
    extractAuthorities(Claims claims) {

        Set<String> roles = new LinkedHashSet<>();

        // role: "ADMIN"
        String singleRole = safeString(claims.get("role"));
        if (!isBlank(singleRole)) roles.add(singleRole);

        // roles: ["ADMIN","HR"]
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
                if (!isBlank(s))
                    roles.add(s.replaceFirst("^ROLE_", ""));
            });
        }

        // scope: "admin user"
        String scope = safeString(claims.get("scope"));
        if (!isBlank(scope)) {
            for (String s : scope.split("\\s+")) {
                if (!isBlank(s))
                    roles.add(s.toUpperCase(Locale.ROOT));
            }
        }

        return roles.stream()
                .filter(r -> !isBlank(r))
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}