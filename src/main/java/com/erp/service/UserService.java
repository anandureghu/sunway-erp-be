package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.User;
import com.erp.dto.security.ChangePasswordRequest;
import com.erp.dto.security.ProfileResponse;
import com.erp.dto.hr.UserDetailsDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.service.security.CustomUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository     userRepo;
    private final EmployeeRepository employeeRepo;
    private final PasswordEncoder    passwordEncoder;

    public UserService(UserRepository userRepo,
                       EmployeeRepository employeeRepo,
                       PasswordEncoder passwordEncoder) {
        this.userRepo        = userRepo;
        this.employeeRepo    = employeeRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // ── existing — untouched ──────────────────────────────────────────────

    public UserDetailsDTO getUserDetails(Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Employee emp = employeeRepo.findByUserId(id).orElse(null);

        return UserDetailsDTO.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())
                .companyRole(user.getCompanyRole())
                .employeeId(emp != null ? emp.getId() : null)
                .employeeNo(emp != null ? emp.getEmployeeNo() : null)
                .firstName(emp != null ? emp.getFirstName() : null)
                .lastName(emp != null ? emp.getLastName() : null)
                .phoneNo(emp != null ? emp.getPhoneNo() : null)
                .companyId(emp != null && emp.getCompany() != null
                        ? emp.getCompany().getId() : null)
                .companyName(emp != null && emp.getCompany() != null
                        ? emp.getCompany().getCompanyName() : null)
                .departmentId(emp != null && emp.getDepartment() != null
                        ? emp.getDepartment().getId() : null)
                .departmentName(emp != null && emp.getDepartment() != null
                        ? emp.getDepartment().getDepartmentName() : null)
                .build();
    }

    // ── get profile ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        CustomUserPrincipal principal = currentPrincipal();

        if (!principal.getId().equals(userId) && !isAdmin(principal)) {
            throw new AccessDeniedException("Access denied");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Employee emp = employeeRepo.findByUserId(userId).orElse(null);

        return ProfileResponse.from(user, emp);
    }

    // ── change password ───────────────────────────────────────────────────

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        CustomUserPrincipal principal = currentPrincipal();

        if (!principal.getId().equals(userId) && !isAdmin(principal)) {
            throw new AccessDeniedException("Access denied");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must differ from current password");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setForcePasswordReset(false);
        userRepo.save(user);
    }

    // ── private helpers ───────────────────────────────────────────────────

    private CustomUserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserPrincipal p)) {
            throw new RuntimeException("No authenticated user found");
        }
        return p;
    }

    private boolean isAdmin(CustomUserPrincipal p) {
        String role = p.getRole().name();
        return role.equals("ADMIN") || role.equals("SUPER_ADMIN");
    }
}