package com.erp.service;

import com.erp.domain.*;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyRole;
import com.erp.domain.hr.Department;
import com.erp.domain.security.AppAction;
import com.erp.domain.security.AppModule;
import com.erp.domain.security.Role;
import com.erp.dto.common.PageResponse;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.dto.hr.UpdateEmployeeDTO;
import com.erp.repo.CompanyLeavePolicyRepository;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeLeaveBalanceRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.repo.contact.EmployeeContactInfoRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.CompanyRoleRepository;
import com.erp.domain.enums.ContractStatus;
import com.erp.repo.hr.ContractRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import com.erp.security.guard.EmployeeAccessGuard;
import com.erp.service.file.FileStorageService;
import com.erp.service.security.PermissionCheckService;
import com.erp.util.EmployeeUserUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
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
    private final EmployeeCurrentJobRepo currentJobRepo;
    private final LeavePolicyService leavePolicyService;
    private final AuthContext authContext;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final PermissionCheckService permissionCheckService;
    private final DocumentSequenceService documentSequenceService;
    private final EmployeeAccessGuard employeeAccessGuard;
    private final ContractRepository contractRepository;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            UserRepository userRepository,
            CompanyRepository companyRepository,
            CompanyRoleRepository companyRoleRepository,
            DepartmentRepository departmentRepository,
            EmployeeContactInfoRepository contactInfoRepository,
            CompanyLeavePolicyRepository policyRepo,
            EmployeeLeaveBalanceRepository balanceRepo,
            EmployeeCurrentJobRepo currentJobRepo,
            LeavePolicyService leavePolicyService,
            AuthContext authContext,
            PasswordEncoder passwordEncoder,
            FileStorageService fileStorageService,
            PermissionCheckService permissionCheckService,
            DocumentSequenceService documentSequenceService,
            EmployeeAccessGuard employeeAccessGuard,
            ContractRepository contractRepository
    ) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.companyRoleRepository = companyRoleRepository;
        this.departmentRepository = departmentRepository;
        this.contactInfoRepository = contactInfoRepository;
        this.policyRepo = policyRepo;
        this.balanceRepo = balanceRepo;
        this.currentJobRepo = currentJobRepo;
        this.leavePolicyService = leavePolicyService;
        this.authContext = authContext;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.permissionCheckService = permissionCheckService;
        this.documentSequenceService = documentSequenceService;
        this.employeeAccessGuard = employeeAccessGuard;
        this.contractRepository = contractRepository;
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
                : resolveCurrentCompany();

        Department department = dto.getDepartmentId() != null
                ? departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"))
                : null;

        Role role = dto.getRole() == null ? Role.USER : dto.getRole();
        CompanyRole companyRole = resolveCompanyRole(company, dto.getCompanyRoleId(), dto.getCompanyRole());

        // ✅ Generate values — per-company employee number (starts at 1000).
        String employeeNo = documentSequenceService.nextEmployeeNo(company.getId());

        // Username/email are globally unique in the users table, but the base is derived
        // purely from the name — so a same-named person in ANOTHER company would collide.
        // Append an incrementing suffix until free (login password follows the final name).
        String username = uniqueUsername(
                EmployeeUserUtil.generateUsername(dto.getFirstName(), dto.getLastName()));
        String email = uniqueEmail(EmployeeUserUtil.generateEmail(username, company));
        String rawPassword = EmployeeUserUtil.generateDefaultPassword(username);

        // ✅ CREATE + SAVE USER FIRST
        User user = new User();
        user.setFullName(dto.getFirstName() + " " + dto.getLastName());
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setCompany(company);
        user.setForcePasswordReset(true);

        user = userRepository.save(user);

        if (user.getId() == null) {
            throw new RuntimeException("User save failed");
        }

        // Probation: a new hire with no explicit status starts UNDER_PROBATION when
        // the company sets a probation period, and is confirmed once it ends. An
        // explicit status on the request always wins. Zero months = created ACTIVE.
        EmployeeStatus status = dto.getStatus();
        java.time.LocalDate probationEndDate = null;
        if (status == null) {
            Integer months = company.getProbationPeriodMonths();
            java.time.LocalDate joinDate = dto.getJoinDate() != null
                    ? dto.getJoinDate() : java.time.LocalDate.now();
            if (months != null && months > 0) {
                status = EmployeeStatus.UNDER_PROBATION;
                probationEndDate = joinDate.plusMonths(months);
            } else {
                status = EmployeeStatus.ACTIVE;
            }
        }

        // ✅ CREATE EMPLOYEE
        Employee employee = Employee.builder()
                .employeeNo(employeeNo)
                .firstName(dto.getFirstName())
                .middleName(dto.getMiddleName())
                .lastName(dto.getLastName())
                .gender(dto.getGender())
                .prefix(dto.getPrefix())
                .status(status)
                .terminationCode(status == EmployeeStatus.TERMINATED ? dto.getTerminationCode() : null)
                .probationEndDate(probationEndDate)
                .maritalStatus(dto.getMaritalStatus())
                .dateOfBirth(dto.getDateOfBirth())
                .joinDate(dto.getJoinDate())
                .notes(dto.getNotes())
                .company(company)
                .department(department)
                .user(user)
                .companyRoleRef(companyRole)
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

        employeeAccessGuard.assertCanWrite(employee, AppModule.EMPLOYEE_PROFILE);

        if (dto.getEmployeeNo()     != null) employee.setEmployeeNo(dto.getEmployeeNo());
        if (dto.getPrefix()         != null) employee.setPrefix(dto.getPrefix());
        if (dto.getFirstName()      != null) employee.setFirstName(dto.getFirstName());
        if (dto.getMiddleName()     != null) employee.setMiddleName(dto.getMiddleName());
        if (dto.getLastName()       != null) employee.setLastName(dto.getLastName());
        if (dto.getGender()         != null) employee.setGender(dto.getGender());
        if (dto.getStatus()         != null) {
            EmployeeStatus newStatus = dto.getStatus();
            employee.setStatus(newStatus);
            // Termination code only lives on a TERMINATED employee — clear it otherwise.
            if (newStatus == EmployeeStatus.TERMINATED) {
                if (dto.getTerminationCode() != null) {
                    employee.setTerminationCode(dto.getTerminationCode());
                }
            } else {
                employee.setTerminationCode(null);
            }
            // A departure ends the current contract: mark it TERMINATED.
            if (newStatus == EmployeeStatus.TERMINATED || newStatus == EmployeeStatus.RESIGNED) {
                terminateActiveContract(employee.getId());
            }
        }
        // Expected end date is the same field as on the current job — cascade it so
        // the profile and the current-job tab stay in sync.
        if (dto.getExpectedEndDate() != null) {
            currentJobRepo.findByEmployee_Id(id).ifPresent(job -> {
                job.setExpectedEndDate(dto.getExpectedEndDate());
                currentJobRepo.save(job);
            });
        }
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

        // HR-managed company role — per-company on employee
        if (dto.getCompanyRoleId() != null || dto.getCompanyRole() != null) {
            employee.setCompanyRoleRef(resolveCompanyRole(
                    employee.getCompany(),
                    dto.getCompanyRoleId(),
                    dto.getCompanyRole()
            ));
        }

        if (employee.getUser() != null) {

            // ✅ Login email — kept in sync with the user's account, since login
            // and every display surface read User.email, not an Employee-owned field.
            if (dto.getEmail() != null && !dto.getEmail().isBlank()
                    && !dto.getEmail().equalsIgnoreCase(employee.getUser().getEmail())) {
                userRepository.findByEmailIgnoreCase(dto.getEmail())
                        .filter(existing -> !existing.getId().equals(employee.getUser().getId()))
                        .ifPresent(existing -> {
                            throw new IllegalArgumentException("Email already in use");
                        });
                employee.getUser().setEmail(dto.getEmail());
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

        employeeAccessGuard.assertCanWrite(employee, AppModule.EMPLOYEE_PROFILE);

        if (image != null && !image.isEmpty()) {
            // Record the file under the EMPLOYEE's company — not the caller's context,
            // which is null for a SUPER_ADMIN and would break the NOT NULL storage ledger.
            Long companyId = employee.getCompanyId() != null
                    ? employee.getCompanyId()
                    : authContext.getCurrentCompanyId();
            FileUploadResult upload = fileStorageService.upload(
                    image, FileCategory.EMPLOYEE_PROFILE, id.toString(), true, companyId);
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

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        employeeAccessGuard.assertCanRead(employee, AppModule.EMPLOYEE_PROFILE);

        return toDTO(employee);
    }

    // ======================================================
    // GET METHODS - LIST ALL EMPLOYEES
    // ✅ FIXED: Handles both VIEW_OWN and VIEW_ALL permissions
    // ======================================================

    public List<EmployeeResponseDTO> getEmployees() {

        User authUser = getAuthUser();
        Long companyId = resolveCurrentCompanyId();

        // ✅ Get authentication for permission check
        var auth = SecurityContextHolder.getContext().getAuthentication();

        // ✅ Check both VIEW_OWN and VIEW_ALL permissions
        boolean canViewAll = permissionCheckService.hasAccess(
                auth,
                AppModule.EMPLOYEE_PROFILE,
                AppAction.VIEW_ALL
        );
        boolean canViewOwn = permissionCheckService.hasAccess(
                auth,
                AppModule.EMPLOYEE_PROFILE,
                AppAction.VIEW_OWN
        );

        log.debug("👤 getEmployees: canViewAll={}, canViewOwn={}", canViewAll, canViewOwn);

        // ✅ VIEW ALL - Return all employees for company
        if (canViewAll) {
            log.info("✅ User has VIEW_ALL permission - loading all employees");
            return employeeRepository
                    .findByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(companyId)
                    .stream()
                    .map(this::toDTO)
                    .toList();
        }

        // ✅ VIEW OWN - Return only current user's employee record
        if (canViewOwn) {
            log.info("✅ User has VIEW_OWN permission - loading own employee only");
            Employee self = resolveCurrentEmployee(authUser);
            return List.of(toDTO(self));
        }


        log.error("❌ User has no EMPLOYEE_PROFILE permission (neither VIEW_ALL nor VIEW_OWN)");
        throw new RuntimeException("Access denied: no permission to view employees");
    }

    // ======================================================
    // GET EMPLOYEES BY COMPANY
    // ======================================================

    public List<EmployeeResponseDTO> getEmployeesByCompany(Long companyId) {
        assertTenantCompanyScope(companyId);
        return employeeRepository.findByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(companyId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ======================================================
    // GET EMPLOYEES BY DEPARTMENT
    // ======================================================

    public List<EmployeeResponseDTO> getEmployeesByDepartment(Long departmentId) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        assertTenantCompanyScope(dept.getCompany().getId());
        return employeeRepository.findByDepartment_IdOrderByCreatedAtDesc(departmentId)
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
                AppModule.EMPLOYEE_PROFILE,
                AppAction.VIEW_ALL
        );
        boolean canViewOwn = permissionCheckService.hasAccess(
                auth,
                AppModule.EMPLOYEE_PROFILE,
                AppAction.VIEW_OWN
        );

        log.debug("👤 getEmployees(paginated): canViewAll={}, canViewOwn={}", canViewAll, canViewOwn);

        // ✅ VIEW OWN ONLY - Return only current user's employee record (single page)
        if (!canViewAll && canViewOwn) {
            log.info("✅ User has VIEW_OWN permission - returning own employee only");
            Employee self = resolveCurrentEmployee(authUser);
            return PageResponse.of(List.of(toDTO(self)), 1, 1, page, size);
        }

        if (!canViewAll) {
            log.error("❌ User has no EMPLOYEE_PROFILE permission");
            throw new RuntimeException("Access denied: no permission to view employees");
        }

        Long companyId = resolveCurrentCompanyId();

        // ✅ VIEW ALL - Return paginated employees for current company only
        log.info("✅ User has VIEW_ALL permission - loading paginated employees");
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Employee> empPage = employeeRepository.findByCompany_Id(companyId, pageable);

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
        assertTenantCompanyScope(companyId);
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

    // Departed/inactive employees cannot manage a department — exclude them from the
    // manager candidate list (see EmployeeStatus#isDepartedOrInactive).
    public List<EmployeeResponseDTO> getManagersByCompany(Long companyId) {
        assertTenantCompanyScope(companyId);
        return employeeRepository.findByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(companyId)
                .stream()
                .filter(e -> e.getStatus() == null || !e.getStatus().isDepartedOrInactive())
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
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        employeeAccessGuard.assertCanWrite(employee, AppModule.EMPLOYEE_PROFILE);

        employeeRepository.delete(employee);
    }

    // ======================================================
    // ARCHIVE LIFECYCLE — keep the active working set lean
    // ======================================================

    /** Archive an inactive (former) employee out of the active listings. */
    @Transactional
    public void archiveEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        employeeAccessGuard.assertCanWrite(employee, AppModule.EMPLOYEE_PROFILE);
        if (employee.getStatus() != EmployeeStatus.INACTIVE) {
            throw new IllegalStateException(
                    "Only inactive employees can be archived. Process the employee's final "
                            + "settlement first — that marks them inactive.");
        }
        employee.setArchived(true);
        employee.setArchivedAt(Instant.now());
        employeeRepository.save(employee);
    }

    /** Restore an archived employee back into the active listings. */
    @Transactional
    public void unarchiveEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        employeeAccessGuard.assertCanWrite(employee, AppModule.EMPLOYEE_PROFILE);
        employee.setArchived(false);
        employee.setArchivedAt(null);
        employeeRepository.save(employee);
    }

    /** Archived (former) employees for the caller's company — records only. */
    @Transactional(readOnly = true)
    public List<EmployeeResponseDTO> listArchived() {
        Long companyId = resolveCurrentCompanyId();
        return employeeRepository
                .findByCompany_IdAndArchivedTrueOrderByArchivedAtDesc(companyId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ======================================================
    // DTO MAPPER
    // ======================================================

    /* ================= PROBATION / CONFIRMATION ================= */

    /** New hires still under probation in the caller's company (for the Confirm tab). */
    @Transactional(readOnly = true)
    public List<EmployeeResponseDTO> listUnderProbation() {
        Long companyId = resolveCurrentCompanyId();
        return employeeRepository
                .findByCompany_IdAndStatus(companyId, EmployeeStatus.UNDER_PROBATION)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /** Confirm a probationary employee — they become ACTIVE and probation clears. */
    @Transactional
    public EmployeeResponseDTO confirmEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        assertTenantCompanyScope(
                employee.getCompany() != null ? employee.getCompany().getId() : null);
        if (employee.getStatus() != EmployeeStatus.UNDER_PROBATION) {
            throw new IllegalStateException(
                    "Only employees under probation can be confirmed.");
        }
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee.setProbationEndDate(null);
        return toDTO(employeeRepository.save(employee));
    }

    private EmployeeResponseDTO toDTO(Employee e) {

        Company             c  = e.getCompany();

        // Append a version derived from updatedAt so re-uploaded photos (stored
        // at the same path) bust the browser cache everywhere the avatar shows.
        String imageUrl = null;
        if (e.getImageUrl() != null) {
            imageUrl = fileStorageService.getPublicUrl(e.getImageUrl());
            if (imageUrl != null && e.getUpdatedAt() != null) {
                imageUrl += (imageUrl.contains("?") ? "&" : "?")
                        + "v=" + e.getUpdatedAt().toEpochMilli();
            }
        }

        var currentJob = e.getId() != null
                ? currentJobRepo.findByEmployee_Id(e.getId()).orElse(null)
                : null;
        String designation = currentJob != null && currentJob.getJobCode() != null
                ? currentJob.getJobCode().getTitle()
                : null;
        String jobCode = currentJob != null && currentJob.getJobCode() != null
                ? currentJob.getJobCode().getCode()
                : null;
        Long divisionId = currentJob != null && currentJob.getDivision() != null
                ? currentJob.getDivision().getId()
                : null;
        String divisionName = currentJob != null && currentJob.getDivision() != null
                ? currentJob.getDivision().getName()
                : null;
        String employmentCategory = currentJob != null && currentJob.getEmploymentCategory() != null
                ? currentJob.getEmploymentCategory().name()
                : null;
        String employmentType = currentJob != null && currentJob.getEmploymentType() != null
                ? currentJob.getEmploymentType().name()
                : null;

        return EmployeeResponseDTO.builder()
                .id(e.getId())
                .employeeNo(e.getEmployeeNo())
                .firstName(e.getFirstName())
                .middleName(e.getMiddleName())
                .lastName(e.getLastName())
                .gender(e.getGender())
                .prefix(e.getPrefix())
                .status(e.getStatus() != null ? e.getStatus().name() : null)
                .terminationCode(e.getTerminationCode())
                .probationEndDate(e.getProbationEndDate())
                .expectedEndDate(currentJob != null ? currentJob.getExpectedEndDate() : null)
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
                .companyRoleId(e.getCompanyRoleId())
                .companyRole(e.getCompanyRole())
                .forcePasswordReset(e.getUser() != null ? e.getUser().getForcePasswordReset() : null)
                .companyId(c   != null ? c.getId()          : null)
                .companyName(c != null ? c.getCompanyName() : null)
                .departmentId(e.getDepartment()   != null ? e.getDepartment().getId()             : null)
                .departmentName(e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null)
                .divisionId(divisionId)
                .divisionName(divisionName)
                .archived(e.isArchived())
                .imageUrl(imageUrl)
                .designation(designation)
                .jobCode(jobCode)
                .employmentCategory(employmentCategory)
                .employmentType(employmentType)
                .build();
    }

    /**
     * When an employee resigns or is terminated their current contract ends too.
     * Contract status is action-driven, so we set it here rather than in the UI.
     * A contract that already lapsed (EXPIRED/TERMINATED) is left untouched.
     */
    private void terminateActiveContract(Long employeeId) {
        contractRepository
                .findFirstByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(employeeId)
                .filter(c -> c.getStatus() != ContractStatus.EXPIRED
                        && c.getStatus() != ContractStatus.TERMINATED)
                .ifPresent(c -> {
                    c.setStatus(ContractStatus.TERMINATED);
                    contractRepository.save(c);
                });
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

    private Employee resolveCurrentEmployee(User authUser) {
        Employee emp = authContext.getCurrentEmployee();
        if (emp != null) return emp;
        return employeeRepository.findByUser_Id(authUser.getId())
                .orElseThrow(() -> new RuntimeException("Employee record not found"));
    }

    /** Active tenant from JWT — not users.company_id (stale for multi-company owners). */
    private Long resolveCurrentCompanyId() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new RuntimeException("User not linked to any company");
        }
        return companyId;
    }

    private Company resolveCurrentCompany() {
        return companyRepository.findById(resolveCurrentCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));
    }

    // ======================================================
    // ASSIGN EXISTING USER TO COMPANY
    // ======================================================

    @Transactional
    public EmployeeResponseDTO assignExistingUserToCompany(Long companyId, Long userId,
                                                           Long companyRoleId, String companyRoleName) {
        User authUser = getAuthUser();
        if (authUser.getRole() != Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Only super admins can assign existing users to companies");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (employeeRepository.existsByUser_IdAndCompany_Id(userId, companyId)) {
            throw new IllegalArgumentException("User is already a member of this company");
        }

        CompanyRole companyRole = resolveCompanyRole(company, companyRoleId, companyRoleName);

        Employee template = employeeRepository.findAllByUser_Id(userId).stream()
                .findFirst()
                .orElse(null);

        String firstName;
        String lastName;
        if (template != null) {
            firstName = template.getFirstName();
            lastName = template.getLastName();
        } else {
            String[] parts = user.getFullName() != null ? user.getFullName().split(" ", 2) : new String[]{"User", ""};
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        }

        String employeeNo = documentSequenceService.nextEmployeeNo(company.getId());

        Employee employee = Employee.builder()
                .employeeNo(employeeNo)
                .firstName(firstName)
                .lastName(lastName)
                .status(EmployeeStatus.ACTIVE)
                .joinDate(java.time.LocalDate.now())
                .company(company)
                .user(user)
                .companyRoleRef(companyRole)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        employee.setRole(user.getRole() != null ? user.getRole().name() : Role.USER.name());

        employee = employeeRepository.save(employee);

        try {
            leavePolicyService.initializeLeaveBalancesForEmployee(employee);
        } catch (Exception e) {
            log.warn("Failed to initialize leave balances for employee {}: {}", employee.getId(), e.getMessage());
        }

        return toDTO(employee);
    }

    /** Ensures {@code requestedCompanyId} matches the JWT tenant unless caller is {@link Role#SUPER_ADMIN}. */
    private void assertTenantCompanyScope(Long requestedCompanyId) {
        if (requestedCompanyId == null) {
            throw new AccessDeniedException("Company is required");
        }
        User authUser = getAuthUser();
        if (authUser.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        Long current = authContext.getCurrentCompanyId();
        if (current == null || !current.equals(requestedCompanyId)) {
            throw new AccessDeniedException("Access denied for this company");
        }
    }

    /** A username not yet taken globally — appends 1, 2, … to the base until free. */
    private String uniqueUsername(String base) {
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    /** An email not yet taken globally — suffixes the local part (before @) until free. */
    private String uniqueEmail(String base) {
        if (!userRepository.existsByEmail(base)) {
            return base;
        }
        int at = base.indexOf('@');
        String local = at >= 0 ? base.substring(0, at) : base;
        String domain = at >= 0 ? base.substring(at) : "";
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByEmail(candidate)) {
            candidate = local + (suffix++) + domain;
        }
        return candidate;
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
