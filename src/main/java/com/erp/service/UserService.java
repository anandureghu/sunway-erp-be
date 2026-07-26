package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.User;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.dto.security.AdminResetPasswordRequest;
import com.erp.dto.security.ChangePasswordRequest;
import com.erp.dto.security.ProfileResponse;
import com.erp.dto.security.UpdateSecuritySettingsRequest;
import com.erp.dto.hr.UserDetailsDTO;
import com.erp.dto.hr.UserSearchResultDTO;
import com.erp.domain.security.Role;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.security.CustomUserPrincipal;
import com.erp.service.security.PermissionCheckService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCheckService permissionCheckService;
    private final AuthContext authContext;

    private final com.erp.service.file.FileStorageService fileStorageService;

    public UserService(UserRepository userRepo,
                       EmployeeRepository employeeRepo,
                       PasswordEncoder passwordEncoder,
                       PermissionCheckService permissionCheckService,
                       AuthContext authContext,
                       com.erp.service.file.FileStorageService fileStorageService) {
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.passwordEncoder = passwordEncoder;
        this.permissionCheckService = permissionCheckService;
        this.authContext = authContext;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Resolve an employee's stored blob path into a servable public URL. The image
     * is persisted as a relative path (e.g. "employees/2/profile.png"); rendering it
     * directly would 404, so callers must expose the resolved URL.
     */
    private String resolveEmployeeImageUrl(Employee emp) {
        if (emp == null || emp.getImageUrl() == null || emp.getImageUrl().isBlank()) {
            return null;
        }
        return fileStorageService.getPublicUrl(emp.getImageUrl());
    }

    // ======================================================
    // USER DETAILS (SAFE)
    // ======================================================

    public UserDetailsDTO getUserDetails(Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));

        User current = userRepo.findById(authContext.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (current.getRole() != Role.SUPER_ADMIN) {
            Long targetCompanyId = resolveTargetUserCompanyId(user);
            Long jwtCompanyId = authContext.getCurrentCompanyId();
            if (jwtCompanyId == null || targetCompanyId == null
                    || !jwtCompanyId.equals(targetCompanyId)) {
                throw new AccessDeniedException("Access denied");
            }
        }

        Employee emp = resolveEmployeeForContext(id);

        return UserDetailsDTO.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .username(user.getUsername())

                // ✅ ENUM role (safe)
                .role(user.getRole())

                // ✅ COMPANY ROLE from active employee membership
                .companyRoleId(emp != null ? emp.getCompanyRoleId() : user.getCompanyRoleId())
                .companyRole(emp != null ? emp.getCompanyRole() : user.getCompanyRole())

                .employeeId(emp != null ? emp.getId() : null)
                .employeeNo(emp != null ? emp.getEmployeeNo() : null)
                .firstName(emp != null ? emp.getFirstName() : null)
                .lastName(emp != null ? emp.getLastName() : null)
                .phoneNo(emp != null ? emp.getPhoneNo() : null)
                .imageUrl(resolveEmployeeImageUrl(emp))

                .companyId(resolveActiveCompanyIdForDetails(id, emp, user))

                .companyName(
                        emp != null && emp.getCompany() != null
                                ? emp.getCompany().getCompanyName()
                                : null
                )

                .departmentId(
                        emp != null && emp.getDepartment() != null
                                ? emp.getDepartment().getId()
                                : null
                )

                .departmentName(
                        emp != null && emp.getDepartment() != null
                                ? emp.getDepartment().getDepartmentName()
                                : null
                )
                .build();
    }

    private Long resolveTargetUserCompanyId(User user) {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId != null) {
            return employeeRepo.findByUser_IdAndCompany_Id(user.getId(), companyId)
                    .map(e -> e.getCompany() != null ? e.getCompany().getId() : null)
                    .orElse(companyId);
        }
        Employee emp = employeeRepo.findByUser_Id(user.getId()).orElse(null);
        if (emp != null && emp.getCompany() != null) {
            return emp.getCompany().getId();
        }
        return authContext.getCurrentCompanyId();
    }

    private Long resolveActiveCompanyIdForDetails(Long userId, Employee emp, User user) {
        if (userId.equals(authContext.getCurrentUserId())) {
            Long jwtCompanyId = authContext.getCurrentCompanyId();
            if (jwtCompanyId != null) {
                return jwtCompanyId;
            }
        }
        if (emp != null && emp.getCompany() != null) {
            return emp.getCompany().getId();
        }
        return authContext.getCurrentCompanyId();
    }

    private Employee resolveEmployeeForContext(Long userId) {
        if (userId.equals(authContext.getCurrentUserId())) {
            Employee current = authContext.getCurrentEmployee();
            if (current != null) return current;
        }
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId != null) {
            return employeeRepo.findByUser_IdAndCompany_Id(userId, companyId).orElse(null);
        }
        return employeeRepo.findByUser_Id(userId).orElse(null);
    }

    // ======================================================
    // PROFILE
    // ======================================================

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        CustomUserPrincipal principal = currentPrincipal();

        if (!principal.getId().equals(userId)
                && !isSuperAdmin(principal)
                && !(isAdmin(principal) && isSameCompanyAsCaller(principal, userId))) {
            throw new AccessDeniedException("Access denied");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Employee emp = resolveEmployeeForContext(userId);

        ProfileResponse response = ProfileResponse.from(user, emp);
        response.setImageUrl(resolveEmployeeImageUrl(emp));
        return response;
    }

    // ======================================================
    // CHANGE PASSWORD
    // ======================================================

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        CustomUserPrincipal principal = currentPrincipal();

        if (!principal.getId().equals(userId)
                && !isSuperAdmin(principal)
                && !(isAdmin(principal) && isSameCompanyAsCaller(principal, userId))) {
            throw new AccessDeniedException("Access denied");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setForcePasswordReset(false);

        userRepo.save(user);
    }

    @Transactional
    public ProfileResponse updateSecuritySettings(Long userId, UpdateSecuritySettingsRequest req) {
        CustomUserPrincipal principal = currentPrincipal();

        if (!principal.getId().equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        user.setTwoFactorEnabled(Boolean.TRUE.equals(req.getTwoFactorEnabled()));
        userRepo.save(user);

        Employee emp = resolveEmployeeForContext(userId);
        ProfileResponse response = ProfileResponse.from(user, emp);
        response.setImageUrl(resolveEmployeeImageUrl(emp));
        return response;
    }

    // ======================================================
    // ADMIN RESET PASSWORD
    // ======================================================

    @Transactional
    public void adminResetPassword(Long userId, AdminResetPasswordRequest req) {
        CustomUserPrincipal principal = currentPrincipal();

        if (!isAdmin(principal) && !canManageUsers()) {
            throw new AccessDeniedException("Access denied");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // Company safety: target user must have membership in caller's tenant.
        // Only SUPER_ADMIN operates across tenants; a plain company ADMIN must
        // still be confined to their own company.
        if (!isSuperAdmin(principal)) {
            if (!isSameCompanyAsCaller(principal, userId)) {
                throw new AccessDeniedException("Access denied (different company)");
            }
        }

        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setForcePasswordReset(false);

        userRepo.save(user);
    }

    // ======================================================
    // USER SEARCH (SUPER_ADMIN)
    // ======================================================

    public List<UserSearchResultDTO> searchUsers(String query) {
        User current = userRepo.findById(authContext.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (current.getRole() != Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Only super admins can search users globally");
        }
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return userRepo.searchByKeyword(query.trim()).stream()
                .map(u -> UserSearchResultDTO.builder()
                        .userId(u.getId())
                        .fullName(u.getFullName())
                        .email(u.getEmail())
                        .username(u.getUsername())
                        .companyNames(employeeRepo.findAllByUser_Id(u.getId()).stream()
                                .map(e -> e.getCompany() != null ? e.getCompany().getCompanyName() : null)
                                .filter(n -> n != null && !n.isBlank())
                                .collect(Collectors.toList()))
                        .build())
                .toList();
    }

    // ======================================================
    // HELPERS
    // ======================================================

    private CustomUserPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof CustomUserPrincipal p)) {
            throw new RuntimeException("No authenticated user found");
        }

        return p;
    }

    private boolean isAdmin(CustomUserPrincipal p) {
        if (p.getRole() == null) return false;

        String role = p.getRole().name();

        return role.equals("ADMIN") || role.equals("SUPER_ADMIN");
    }

    private boolean isSuperAdmin(CustomUserPrincipal p) {
        return p.getRole() == Role.SUPER_ADMIN;
    }

    /**
     * True when the target user has an Employee membership in the caller's
     * own company. Used to confine a plain company ADMIN (as opposed to
     * SUPER_ADMIN) to their own tenant.
     */
    private boolean isSameCompanyAsCaller(CustomUserPrincipal principal, Long targetUserId) {
        Long callerCompanyId = principal.getCompanyId();
        return callerCompanyId != null
                && employeeRepo.existsByUser_IdAndCompany_Id(targetUserId, callerCompanyId);
    }

    private boolean canManageUsers() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        return permissionCheckService.hasAccess(
                auth,
                AppModule.HR_SETTINGS,
                AppAction.EDIT
        );
    }
}