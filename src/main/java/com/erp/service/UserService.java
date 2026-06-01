package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.User;
import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.dto.security.AdminResetPasswordRequest;
import com.erp.dto.security.ChangePasswordRequest;
import com.erp.dto.security.ProfileResponse;
import com.erp.dto.hr.UserDetailsDTO;
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

@Service
public class UserService {

    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCheckService permissionCheckService;
    private final AuthContext authContext;

    public UserService(UserRepository userRepo,
                       EmployeeRepository employeeRepo,
                       PasswordEncoder passwordEncoder,
                       PermissionCheckService permissionCheckService,
                       AuthContext authContext) {
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.passwordEncoder = passwordEncoder;
        this.permissionCheckService = permissionCheckService;
        this.authContext = authContext;
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

        Employee emp = employeeRepo.findByUser_Id(id).orElse(null);

        return UserDetailsDTO.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .username(user.getUsername())

                // ✅ ENUM role (safe)
                .role(user.getRole())

                // ✅ COMPANY ROLE (entity safe)
                .companyRoleId(user.getCompanyRoleId())
                .companyRole(user.getCompanyRole())

                .employeeId(emp != null ? emp.getId() : null)
                .employeeNo(emp != null ? emp.getEmployeeNo() : null)
                .firstName(emp != null ? emp.getFirstName() : null)
                .lastName(emp != null ? emp.getLastName() : null)
                .phoneNo(emp != null ? emp.getPhoneNo() : null)
                .imageUrl(emp != null ? emp.getImageUrl() : null)

                .companyId(
                        emp != null && emp.getCompany() != null
                                ? emp.getCompany().getId()
                                : user.getCompanyId() // ✅ fallback (important fix)
                )

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
        Employee emp = employeeRepo.findByUser_Id(user.getId()).orElse(null);
        if (emp != null && emp.getCompany() != null) {
            return emp.getCompany().getId();
        }
        return user.getCompanyId();
    }

    // ======================================================
    // PROFILE
    // ======================================================

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        CustomUserPrincipal principal = currentPrincipal();

        if (!principal.getId().equals(userId) && !isAdmin(principal)) {
            throw new AccessDeniedException("Access denied");
        }

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        Employee emp = employeeRepo.findByUser_Id(userId).orElse(null);

        return ProfileResponse.from(user, emp);
    }

    // ======================================================
    // CHANGE PASSWORD
    // ======================================================

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
            throw new IllegalArgumentException("New password must be different from current password");
        }

        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setForcePasswordReset(false);

        userRepo.save(user);
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

        // ✅ Company safety check
        if (!isAdmin(principal)
                && principal.getCompanyId() != null
                && user.getCompanyId() != null
                && !principal.getCompanyId().equals(user.getCompanyId())) {
            throw new AccessDeniedException("Access denied (different company)");
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

    private boolean canManageUsers() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        return permissionCheckService.hasAccess(
                auth,
                HrModule.HR_SETTINGS,
                HrAction.EDIT
        );
    }
}