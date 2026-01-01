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

    // ============================================================
    // Get FULL authenticated User entity
    // ============================================================
    public User getCurrentUser() {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        Object details = auth.getDetails();

        if (details instanceof Map<?, ?> map &&
                map.get("claims") instanceof Claims claims) {

            Object userId = claims.get("userId");
            if (userId == null) {
                return null;
            }

            return userRepository.findById(
                    Long.valueOf(String.valueOf(userId))
            ).orElse(null);
        }

        return null;
    }

    public Long getCurrentCompanyId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            Object details = auth.getDetails();
            if (details instanceof Map<?, ?> map && map.get("claims") instanceof Claims claims) {
                Object companyId = claims.get("companyId");
                if (companyId != null) {
                    return Long.valueOf(String.valueOf(companyId));
                }
            }
        }
        return null;
    }

    // ============================================================
    // Convenience methods (still useful)
    // ============================================================
    public Long getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    public String getCurrentUsername() {
        User user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    public String getCurrentUserRole() {
        User user = getCurrentUser();
        return user != null ? user.getRole().name() : null;
    }
}
