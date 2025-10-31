package com.hrmodule.security;

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
import org.springframework.security.core.userdetails.User;
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
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Let CORS preflight pass quickly
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // If already authenticated, don’t do it again
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

            // Username from "username" -> "preferred_username" -> "sub"
            String username = safeString(claims.get("username"));
            if (isBlank(username)) username = safeString(claims.get("preferred_username"));
            if (isBlank(username)) username = claims.getSubject();

            if (isBlank(username)) {
                log.warn("JWT missing username/sub claim");
                chain.doFilter(request, response);
                return;
            }

            // Authorities from role/roles/authorities/scope
            Collection<SimpleGrantedAuthority> authorities = extractAuthorities(claims);

            if (authorities.isEmpty()) {
                // If you prefer default user role, uncomment:
                // authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
            }

            User principal = new User(username, "", authorities);
            var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            if (log.isDebugEnabled()) {
                log.debug("JWT authenticated '{}', authorities={}", username, authorities);
            }
        } catch (Exception ex) {
            // Don’t throw — let the chain continue and your entry point will return 401
            log.debug("JWT parse/authorize failed: {}", ex.getMessage());
        }

        chain.doFilter(request, response);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safeString(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    private static Collection<SimpleGrantedAuthority> extractAuthorities(Claims claims) {
        Set<String> roles = new LinkedHashSet<>();

        // Single "role": "ADMIN"
        String singleRole = safeString(claims.get("role"));
        if (!isBlank(singleRole)) roles.add(singleRole);

        // Array "roles": ["ADMIN","HR"]
        Object arr = claims.get("roles");
        if (arr instanceof Collection<?> c) {
            c.forEach(x -> { String s = safeString(x); if (!isBlank(s)) roles.add(s); });
        }

        // Array "authorities": ["ROLE_ADMIN","ROLE_HR"] (normalize by stripping a leading ROLE_)
        Object auths = claims.get("authorities");
        if (auths instanceof Collection<?> c) {
            c.forEach(x -> {
                String s = safeString(x);
                if (!isBlank(s)) roles.add(s.replaceFirst("^ROLE_", ""));
            });
        }

        // Space-delimited "scope": "read write admin" -> treat as roles if you use it
        String scope = safeString(claims.get("scope"));
        if (!isBlank(scope)) {
            for (String s : scope.split("\\s+")) {
                if (!isBlank(s)) roles.add(s.toUpperCase(Locale.ROOT));
            }
        }

        // Map to ROLE_*
        return roles.stream()
                .filter(r -> !isBlank(r))
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
