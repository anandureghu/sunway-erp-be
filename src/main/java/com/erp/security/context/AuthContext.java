package com.erp.security.context;

import com.erp.domain.User;
import com.erp.repo.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
@Component
public class AuthContext {

    private final UserRepository userRepository;

    public AuthContext(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    private Claims getClaims() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object details = auth.getDetails();

        if (details instanceof Map<?, ?> map &&
                map.get("claims") instanceof Claims claims) {
            return claims;
        }

        return null;
    }

    public Long getCurrentUserId() {
        Claims claims = getClaims();
        if (claims == null) return null;

        Object userId = claims.get("userId");
        return userId != null ? Long.valueOf(String.valueOf(userId)) : null;
    }

    public Long getCurrentCompanyId() {
        Claims claims = getClaims();
        if (claims == null) return null;

        Object companyId = claims.get("companyId");
        return companyId != null ? Long.valueOf(String.valueOf(companyId)) : null;
    }

    public String getCurrentUserRole() {
        Claims claims = getClaims();
        if (claims == null) return null;

        Object role = claims.get("role");
        return role != null ? String.valueOf(role) : null;
    }

    // Only call DB when really needed
    public User getCurrentUser() {
        Long userId = getCurrentUserId();
        return userId != null
                ? userRepository.findById(userId).orElse(null)
                : null;
    }
}