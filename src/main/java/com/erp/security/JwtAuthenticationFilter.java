package com.erp.security;

import com.erp.domain.security.Role;
import com.erp.service.security.CustomUserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

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

            String username = safeString(claims.get("username"));
            if (isBlank(username)) {
                username = safeString(claims.get("preferred_username"));
            }
            if (isBlank(username)) {
                username = claims.getSubject();
            }
            if (isBlank(username)) {
                log.warn("JWT missing username");
                chain.doFilter(request, response);
                return;
            }

            Collection<SimpleGrantedAuthority> authorities = extractAuthorities(claims);
            if (authorities.isEmpty()) {
                log.warn("JWT has no authorities for user '{}'", username);
                chain.doFilter(request, response);
                return;
            }

            Role role = resolveRoleFromAuthorities(authorities);
            if (role == null) {
                log.warn("JWT authorities do not match any Role enum for user '{}': {}", username, authorities);
                chain.doFilter(request, response);
                return;
            }

            Long userId = parseLong(claims.get("userId"));
            if (userId == null) {
                log.warn("JWT missing or invalid userId for user '{}'", username);
                chain.doFilter(request, response);
                return;
            }
            Long employeeId = parseLong(claims.get("employeeId"));
            Long companyId = parseLong(claims.get("companyId"));
            Long companyRoleId = parseLong(claims.get("companyRoleId"));
            String companyRole = safeString(claims.get("companyRole"));

            CustomUserPrincipal principal = new CustomUserPrincipal(
                    userId,
                    employeeId,
                    username,
                    "",
                    role,
                    companyRoleId,
                    companyRole,
                    companyId
            );

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

            Map<String, Object> details = new HashMap<>();
            details.put("web", new WebAuthenticationDetailsSource().buildDetails(request));
            details.put("claims", claims);
            auth.setDetails(details);

            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (ExpiredJwtException ex) {
            log.warn("JWT expired");
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
        }

        chain.doFilter(request, response);
    }

    private static Role resolveRoleFromAuthorities(Collection<SimpleGrantedAuthority> authorities) {
        for (SimpleGrantedAuthority authority : authorities) {
            String raw = authority.getAuthority();
            if (raw == null) {
                continue;
            }

            String roleName = raw.startsWith("ROLE_") ? raw.substring(5) : raw;
            roleName = roleName.trim();
            if (roleName.isEmpty()) {
                continue;
            }

            try {
                return Role.valueOf(roleName);
            } catch (IllegalArgumentException ignored) {
                // Try the next authority.
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Collection<SimpleGrantedAuthority> extractAuthorities(Claims claims) {
        Set<String> roles = new LinkedHashSet<>();

        String singleRole = safeString(claims.get("role"));
        if (!isBlank(singleRole)) {
            roles.add(singleRole.trim());
        }

        Object roleArray = claims.get("roles");
        if (roleArray instanceof Collection<?> collection) {
            collection.forEach(item -> {
                String value = safeString(item);
                if (!isBlank(value)) {
                    roles.add(value.trim());
                }
            });
        }

        Object authorities = claims.get("authorities");
        if (authorities instanceof Collection<?> collection) {
            collection.forEach(item -> {
                String value = safeString(item);
                if (!isBlank(value)) {
                    roles.add(value.replaceFirst("^ROLE_", ""));
                }
            });
        }

        String scope = safeString(claims.get("scope"));
        if (!isBlank(scope)) {
            for (String item : scope.split("\\s+")) {
                if (!isBlank(item)) {
                    roles.add(item.toUpperCase(Locale.ROOT));
                }
            }
        }

        return roles.stream()
                .filter(value -> !isBlank(value))
                .map(value -> value.startsWith("ROLE_") ? value : "ROLE_" + value)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long parseLong(Object value) {
        if (value instanceof Integer integer) {
            return integer.longValue();
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
