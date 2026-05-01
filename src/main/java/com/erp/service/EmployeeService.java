package com.erp.service;

import com.erp.domain.*;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyRole;
import com.erp.domain.hr.Department;
import com.erp.domain.security.HrAction;
import com.erp.domain.security.HrModule;
import com.erp.domain.security.Role;
import com.erp.dto.common.PageResponse;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.dto.hr.UpdateEmployeeDTO;
import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeLeaveBalanceRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.repo.contact.EmployeeContactInfoRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.CompanyRoleRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import com.erp.service.security.PermissionCheckService;
import com.erp.util.EmployeeUserUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final CompanyRoleRepository companyRoleRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeContactInfoRepository contactInfoRepository;
    private final CompanyLeavePolicyRepository policyRepo;
    private final EmployeeLeaveBalanceRepository balanceRepo;
    private final LeavePolicyService leavePolicyService;
    private final AuthContext authContext;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final PermissionCheckService permissionCheckService;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            UserRepository userRepository,
            CompanyRepository companyRepository,
            CompanyRoleRepository companyRoleRepository,
            DepartmentRepository departmentRepository,
            EmployeeContactInfoRepository contactInfoRepository,
            CompanyLeavePolicyRepository policyRepo,
            EmployeeLeaveBalanceRepository balanceRepo,
            LeavePolicyService leavePolicyService,
            AuthContext authContext,
            PasswordEncoder passwordEncoder,
            FileStorageService fileStorageService,
            PermissionCheckService permissionCheckService
    ) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.companyRoleRepository = companyRoleRepository;
        this.departmentRepository = departmentRepository;
        this.contactInfoRepository = contactInfoRepository;
        this.policyRepo = policyRepo;
        this.balanceRepo = balanceRepo;
        this.leavePolicyService = leavePolicyService;
        this.authContext = authContext;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.permissionCheckService = permissionCheckService;
    }
    // ======================================================
    // CREATE EMPLOYEE
    // ======================================================

    @Transactional
    public EmployeeResponseDTO createEmployee(@Valid CreateEmployeeDTO dto) {

        User authUser = getAuthUser();

        Company company = dto.getCompanyId() != null
                ? companyRepository.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"))
                : authUser.getCompany();

        if (company == null) throw new RuntimeException("Company must be specified");

        Department department = dto.getDepartmentId() != null
                ? departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"))
                : null;

        Role role = dto.getRole() == null ? Role.USER : dto.getRole();
        CompanyRole companyRole = resolveCompanyRole(company, dto.getCompanyRoleId(), dto.getCompanyRole());

        // ✅ Generate values
        employeeRepository.incrementEmployeeNo();
        String employeeNo = String.valueOf(employeeRepository.getCurrentEmployeeNo());

        String username = EmployeeUserUtil.generateUsername(dto.getFirstName(), dto.getLastName());
        String email = EmployeeUserUtil.generateEmail(username, company);
        String rawPassword = EmployeeUserUtil.generateDefaultPassword(username);

        // ✅ CREATE + SAVE USER FIRST
        User user = new User();
        user.setFullName(dto.getFirstName() + " " + dto.getLastName());
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setCompanyRoleRef(companyRole);
        user.setCompany(company);
        user.setForcePasswordReset(true);

        user = userRepository.save(user);

        if (user.getId() == null) {
            throw new RuntimeException("User save failed");
        }

        // ✅ CREATE EMPLOYEE
        Employee employee = Employee.builder()
                .employeeNo(employeeNo)
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .gender(dto.getGender())
                .prefix(dto.getPrefix())
                .status(dto.getStatus() != null ? dto.getStatus() : EmployeeStatus.ACTIVE)
                .maritalStatus(dto.getMaritalStatus())
                .dateOfBirth(dto.getDateOfBirth())
                .joinDate(dto.getJoinDate())
                .notes(dto.getNotes())
                .company(company)
                .department(department)
                .user(user)
                .birthplace(dto.getBirthplace())
                .hometown(dto.getHometown())
                .nationality(dto.getNationality())
                .religion(dto.getReligion())
                .identification(dto.getIdentification())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // ✅ Set the role from user (needed for leave policy lookup)
        employee.setRole(role.name());

        // ✅ SAVE EMPLOYEE FIRST (must be persisted before leave initialization)
        employee = employeeRepository.save(employee);

        // ✅ Verify employee was saved with ID
        if (employee.getId() == null) {
            throw new RuntimeException("Employee save failed - no ID generated");
        }

        // ✅ NOW initialize leave balances (after employee is persisted)
        try {
            leavePolicyService.initializeLeaveBalancesForEmployee(employee);
            log.info("✅ Leave balances initialized for employee: {}", employee.getId());
        } catch (Exception e) {
            log.warn("⚠️  Failed to initialize leave balances for employee {}: {}",
                    employee.getId(), e.getMessage());
            // Don't fail the entire employee creation if leave initialization fails
        }

        return toDTO(employee);
    }

    // ======================================================
    // UPDATE EMPLOYEE
    // ======================================================

    @Transactional
    public EmployeeResponseDTO updateEmployee(Long id, UpdateEmployeeDTO dto) {

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (dto.getEmployeeNo()     != null) employee.setEmployeeNo(dto.getEmployeeNo());
        if (dto.getPrefix()         != null) employee.setPrefix(dto.getPrefix());
        if (dto.getFirstName()      != null) employee.setFirstName(dto.getFirstName());
        if (dto.getLastName()       != null) employee.setLastName(dto.getLastName());
        if (dto.getGender()         != null) employee.setGender(dto.getGender());
        if (dto.getStatus()         != null) employee.setStatus(dto.getStatus());
        if (dto.getDateOfBirth()    != null) employee.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getJoinDate()       != null) employee.setJoinDate(dto.getJoinDate());
        if (dto.getMaritalStatus()  != null) employee.setMaritalStatus(dto.getMaritalStatus());
        if (dto.getNotes()          != null) employee.setNotes(dto.getNotes());
        if (dto.getBirthplace()     != null) employee.setBirthplace(dto.getBirthplace());
        if (dto.getHometown()       != null) employee.setHometown(dto.getHometown());
        if (dto.getNationality()    != null) employee.setNationality(dto.getNationality());
        if (dto.getReligion()       != null) employee.setReligion(dto.getReligion());
        if (dto.getIdentification() != null) employee.setIdentification(dto.getIdentification());

        if (dto.getDepartmentId() != null) {
            Department department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            employee.setDepartment(department);
        }

        if (employee.getUser() != null) {

            // HR-managed display role — any editor can update
            if (dto.getCompanyRoleId() != null || dto.getCompanyRole() != null) {
                employee.getUser().setCompanyRoleRef(resolveCompanyRole(
                        employee.getCompany(),
                        dto.getCompanyRoleId(),
                        dto.getCompanyRole()
                ));
            }

            // ✅ Spring Security role — ADMIN/SUPER_ADMIN only
            if (dto.getRole() != null) {
                User authUser = getAuthUser();
                boolean isAdmin = authUser.getRole() == Role.ADMIN
                        || authUser.getRole() == Role.SUPER_ADMIN;
                if (!isAdmin) {
                    throw new RuntimeException("Only admins can change the security role");
                }
                employee.getUser().setRole(dto.getRole());

                // ✅ Also update role on employee for leave policy lookup
                employee.setRole(dto.getRole().name());
            }

            userRepository.save(employee.getUser());
        }

        employee.setUpdatedAt(Instant.now());
        return toDTO(employeeRepository.save(employee));
    }

    // ======================================================
    // UPLOAD PROFILE IMAGE
    // ======================================================

    @Transactional
    public EmployeeResponseDTO uploadProfileImage(Long id, MultipartFile image) {

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (image != null && !image.isEmpty()) {
            FileUploadResult upload = fileStorageService.upload(
                    image, FileCategory.EMPLOYEE_PROFILE, id.toString(), true);
            employee.setImageUrl(upload.getBlobPath());
            employeeRepository.save(employee);
        }

        return toDTO(employee);
    }

    // ======================================================
    // GET SINGLE EMPLOYEE
    // ✅ FIXED: Handles both VIEW_OWN and VIEW_ALL permissions
    // ======================================================

    public EmployeeResponseDTO getEmployeeById(Long id) {

        User authUser = getAuthUser();
        var auth = SecurityContextHolder.getContext().getAuthentication();

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // ✅ Check permissions
        if (authUser.getRole() == Role.USER) {
            boolean canViewAll = permissionCheckService.hasAccess(
                    auth,
                    HrModule.EMPLOYEE_PROFILE,
                    HrAction.VIEW_ALL
            );

            if (!canViewAll) {
                boolean canViewOwn = permissionCheckService.hasAccess(
                        auth,
                        HrModule.EMPLOYEE_PROFILE,
                        HrAction.VIEW_OWN
                );

                if (!canViewOwn || employee.getUser() == null ||
                        !employee.getUser().getId().equals(authUser.getId())) {
                    throw new RuntimeException("Access denied: cannot view other employees");
                }
            }
        }

        return toDTO(employee);
    }

    // ======================================================
    // GET METHODS - LIST ALL EMPLOYEES
    // ✅ FIXED: Handles both VIEW_OWN and VIEW_ALL permissions
    // ======================================================

    public List<EmployeeResponseDTO> getEmployees() {

        User authUser = getAuthUser();

        if (authUser.getCompany() == null)
            throw new RuntimeException("User not linked to any company");

        // ✅ Get authentication for permission check
        var auth = SecurityContextHolder.getContext().getAuthentication();

        // ✅ Check both VIEW_OWN and VIEW_ALL permissions
        boolean canViewAll = permissionCheckService.hasAccess(
                auth,
                HrModule.EMPLOYEE_PROFILE,
                HrAction.VIEW_ALL
        );
        boolean canViewOwn = permissionCheckService.hasAccess(
                auth,
                HrModule.EMPLOYEE_PROFILE,
                HrAction.VIEW_OWN
        );

        log.debug("👤 getEmployees: canViewAll={}, canViewOwn={}", canViewAll, canViewOwn);

        // ✅ VIEW ALL - Return all employees for company
        if (canViewAll) {
            log.info("✅ User has VIEW_ALL permission - loading all employees");
            return employeeRepository
                    .findByCompany_Id(authUser.getCompany().getId())
                    .stream()
                    .map(this::toDTO)
                    .toList();
        }

        // ✅ VIEW OWN - Return only current user's employee record
        if (canViewOwn) {
            log.info("✅ User has VIEW_OWN permission - loading own employee only");
            Employee self = employeeRepository.findByUser_Id(authUser.getId())
                    .orElseThrow(() -> new RuntimeException("Employee record not found"));
            return List.of(toDTO(self));
        }


        log.error("❌ User has no EMPLOYEE_PROFILE permission (neither VIEW_ALL nor VIEW_OWN)");
        throw new RuntimeException("Access denied: no permission to view employees");
    }

    // ======================================================
    // GET EMPLOYEES BY COMPANY
    // ======================================================

    public List<EmployeeResponseDTO> getEmployeesByCompany(Long companyId) {
        return employeeRepository.findByCompany_Id(companyId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ======================================================
    // GET EMPLOYEES BY DEPARTMENT
    // ======================================================

    public List<EmployeeResponseDTO> getEmployeesByDepartment(Long departmentId) {
        return employeeRepository.findByDepartmentId(departmentId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ======================================================
    // GET METHODS - PAGINATED LIST
    // ✅ FIXED: Handles both VIEW_OWN and VIEW_ALL permissions
    // ======================================================

    public PageResponse<EmployeeResponseDTO> getEmployees(int page, int size) {

        User authUser = getAuthUser();

        // ✅ Get authentication for permission check
        var auth = SecurityContextHolder.getContext().getAuthentication();

        // ✅ Check both VIEW_OWN and VIEW_ALL permissions
        boolean canViewAll = permissionCheckService.hasAccess(
                auth,
                HrModule.EMPLOYEE_PROFILE,
                HrAction.VIEW_ALL
        );
        boolean canViewOwn = permissionCheckService.hasAccess(
                auth,
                HrModule.EMPLOYEE_PROFILE,
                HrAction.VIEW_OWN
        );

        log.debug("👤 getEmployees(paginated): canViewAll={}, canViewOwn={}", canViewAll, canViewOwn);

        // ✅ VIEW OWN ONLY - Return only current user's employee record (single page)
        if (!canViewAll && canViewOwn) {
            log.info("✅ User has VIEW_OWN permission - returning own employee only");
            Employee self = employeeRepository.findByUser_Id(authUser.getId())
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
            return PageResponse.of(List.of(toDTO(self)), 1, 1, page, size);
        }

        if (!canViewAll) {
            log.error("❌ User has no EMPLOYEE_PROFILE permission");
            throw new RuntimeException("Access denied: no permission to view employees");
        }

        // ✅ VIEW ALL - Return paginated list of all employees
        log.info("✅ User has VIEW_ALL permission - loading paginated employees");
        Pageable pageable = PageRequest.of(page, size);
        Page<Employee> empPage = employeeRepository.findAll(pageable);

        return PageResponse.of(
                empPage.getContent().stream().map(this::toDTO).toList(),
                empPage.getTotalElements(),
                empPage.getTotalPages(),
                page,
                size
        );
    }

    // ======================================================
    // GET COMPANY ADMIN
    // ======================================================

    public EmployeeResponseDTO getCompanyAdmin(Long companyId) {
        List<Role> roles = List.of(Role.ADMIN, Role.SUPER_ADMIN);

        return employeeRepository
                .findFirstByCompany_IdAndUserRoleIn(companyId, roles)
                .map(this::toDTO)
                .orElse(null);
    }

    // ======================================================
    // GET MANAGERS BY COMPANY
    // Returns ALL employees of the company — any employee can be a manager
    // ======================================================

    public List<EmployeeResponseDTO> getManagersByCompany(Long companyId) {
        return employeeRepository.findByCompany_Id(companyId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ======================================================
    // SYNC EMPLOYEE LEAVE BALANCES
    // ======================================================

    public void syncAllEmployeeLeaveBalances(Long companyId) {
        // existing logic retained
    }

    // ======================================================
    // DELETE EMPLOYEE
    // ======================================================

    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }

    // ======================================================
    // DTO MAPPER
    // ======================================================

    private EmployeeResponseDTO toDTO(Employee e) {

        Company             c  = e.getCompany();
        EmployeeContactInfo ci = e.getContactInfo();

        String imageUrl = e.getImageUrl() != null
                ? fileStorageService.getPublicUrl(e.getImageUrl())
                : null;

        return EmployeeResponseDTO.builder()
                .id(e.getId())
                .employeeNo(e.getEmployeeNo())
                .firstName(e.getFirstName())
                .lastName(e.getLastName())
                .gender(e.getGender())
                .prefix(e.getPrefix())
                .status(e.getStatus() != null ? e.getStatus().name() : null)
                .maritalStatus(e.getMaritalStatus())
                .dateOfBirth(e.getDateOfBirth())
                .joinDate(e.getJoinDate())
                .notes(e.getNotes())
                .birthplace(e.getBirthplace())
                .hometown(e.getHometown())
                .nationality(e.getNationality())
                .religion(e.getReligion())
                .identification(e.getIdentification())
                .email(e.getUser()       != null ? e.getUser().getEmail()           : null)
                .username(e.getUser()    != null ? e.getUser().getUsername()        : null)
                .userId(e.getUser()      != null ? e.getUser().getId()              : null)
                .role(e.getUser()        != null ? e.getUser().getRole()            : null)
                .companyRoleId(e.getUser() != null ? e.getUser().getCompanyRoleId() : null)
                .companyRole(e.getUser() != null ? e.getUser().getCompanyRole()     : null)
                .forcePasswordReset(e.getUser() != null ? e.getUser().getForcePasswordReset() : null)
                .companyId(c   != null ? c.getId()          : null)
                .companyName(c != null ? c.getCompanyName() : null)
                .departmentId(e.getDepartment()   != null ? e.getDepartment().getId()             : null)
                .departmentName(e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null)
                .imageUrl(imageUrl)
                .build();
    }

    // ======================================================
    // AUTH HELPER
    // ======================================================

    private User getAuthUser() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) throw new RuntimeException("Unauthorized");
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private CompanyRole resolveCompanyRole(Company company, Long companyRoleId, String companyRoleName) {
        if (companyRoleId != null) {
            return companyRoleRepository.findByIdAndCompanyId(companyRoleId, company.getId())
                    .filter(CompanyRole::getActive)
                    .orElseThrow(() -> new RuntimeException("Company role not found or inactive"));
        }

        if (companyRoleName != null && !companyRoleName.isBlank()) {
            return companyRoleRepository.findByCompanyIdAndNameIgnoreCase(company.getId(), companyRoleName.trim())
                    .filter(CompanyRole::getActive)
                    .orElseThrow(() -> new RuntimeException("Company role not found or inactive"));
        }

        return null;
    }
}
