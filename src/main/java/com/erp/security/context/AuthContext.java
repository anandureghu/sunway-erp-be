package com.erp.security.context;

import com.erp.domain.Employee;
import com.erp.domain.User;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
@Component
public class AuthContext {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;

    public AuthContext(UserRepository userRepository, EmployeeRepository employeeRepository) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
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

    public Long getCurrentEmployeeId() {
        Claims claims = getClaims();
        if (claims == null) return null;

        Object employeeId = claims.get("employeeId");
        return employeeId != null ? Long.valueOf(String.valueOf(employeeId)) : null;
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

    /** Active employee for the current JWT tenant context. */
    public Employee getCurrentEmployee() {
        Long employeeId = getCurrentEmployeeId();
        if (employeeId != null) {
            return employeeRepository.findById(employeeId).orElse(null);
        }
        Long userId = getCurrentUserId();
        Long companyId = getCurrentCompanyId();
        if (userId == null || companyId == null) return null;
        return employeeRepository.findByUser_IdAndCompany_Id(userId, companyId).orElse(null);
    }
}