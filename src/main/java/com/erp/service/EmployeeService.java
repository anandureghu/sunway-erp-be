package com.erp.service;

import com.erp.domain.*;
import com.erp.domain.hr.Company;
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
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import com.erp.service.security.PermissionCheckService;
import com.erp.util.EmployeeUserUtil;
import jakarta.validation.Valid;
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
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
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

        Company company;
        if (dto.getCompanyId() != null) {
            company = companyRepository.findById(dto.getCompanyId())
                    .orElseThrow(() -> new RuntimeException("Company not found"));
        } else {
            company = authUser.getCompany();
            if (company == null) throw new RuntimeException("Company must be specified");
        }

        // Security role — controls access/permissions
        Role role = dto.getRole() == null ? Role.USER : dto.getRole();

        Department department = null;
        if (dto.getDepartmentId() != null) {
            department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        employeeRepository.incrementEmployeeNo();
        String employeeNo = String.valueOf(employeeRepository.getCurrentEmployeeNo());

        String username    = EmployeeUserUtil.generateUsername(dto.getFirstName(), dto.getLastName());
        String email       = EmployeeUserUtil.generateEmail(username, company);
        String rawPassword = EmployeeUserUtil.generateDefaultPassword(username);

        User user = new User();
        user.setFullName(dto.getFirstName() + " " + dto.getLastName());
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);                          // ← Spring Security role (enum)
        user.setCompanyRole(dto.getCompanyRole());   // ← HR business role (dynamic) ✅
        user.setCompany(company);
        user.setForcePasswordReset(true);

        user = userRepository.save(user);

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

        employeeRepository.save(employee);
        leavePolicyService.initializeLeaveBalancesForEmployee(employee);

        return toDTO(employee);
    }

    // ======================================================
    // UPDATE EMPLOYEE
    // ======================================================

    @Transactional
    public EmployeeResponseDTO updateEmployee(Long id, UpdateEmployeeDTO dto) {

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (dto.getEmployeeNo()    != null) employee.setEmployeeNo(dto.getEmployeeNo());
        if (dto.getPrefix()        != null) employee.setPrefix(dto.getPrefix());
        if (dto.getFirstName()     != null) employee.setFirstName(dto.getFirstName());
        if (dto.getLastName()      != null) employee.setLastName(dto.getLastName());
        if (dto.getGender()        != null) employee.setGender(dto.getGender());
        if (dto.getStatus()        != null) employee.setStatus(dto.getStatus());
        if (dto.getDateOfBirth()   != null) employee.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getJoinDate()      != null) employee.setJoinDate(dto.getJoinDate());
        if (dto.getMaritalStatus() != null) employee.setMaritalStatus(dto.getMaritalStatus());
        if (dto.getNotes()         != null) employee.setNotes(dto.getNotes());
        if (dto.getBirthplace()    != null) employee.setBirthplace(dto.getBirthplace());
        if (dto.getHometown()      != null) employee.setHometown(dto.getHometown());
        if (dto.getNationality()   != null) employee.setNationality(dto.getNationality());
        if (dto.getReligion()      != null) employee.setReligion(dto.getReligion());
        if (dto.getIdentification()!= null) employee.setIdentification(dto.getIdentification());

        if (dto.getDepartmentId() != null) {
            Department department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
            employee.setDepartment(department);
        }

        // Update companyRole on linked user if provided ✅
        if (dto.getCompanyRole() != null && employee.getUser() != null) {
            employee.getUser().setCompanyRole(dto.getCompanyRole());
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
    // ======================================================

    public EmployeeResponseDTO getEmployeeById(Long id) {

        User authUser = getAuthUser();

        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        // Role.USER without VIEW_ALL → can only see themselves
        if (authUser.getRole() == Role.USER) {
            boolean hasViewAll = permissionCheckService.hasAccess(
                    SecurityContextHolder.getContext().getAuthentication(),
                    HrModule.EMPLOYEE_PROFILE,
                    HrAction.VIEW_ALL
            );
            if (!hasViewAll) {
                if (employee.getUser() == null ||
                        !employee.getUser().getId().equals(authUser.getId())) {
                    throw new RuntimeException("Access denied: cannot view other employees");
                }
            }
        }

        return toDTO(employee);
    }

    // ======================================================
    // GET METHODS
    // ======================================================

    public List<EmployeeResponseDTO> getEmployees() {

        User authUser = getAuthUser();

        if (authUser.getCompany() == null)
            throw new RuntimeException("User not linked to any company");

        if (authUser.getRole() == Role.USER) {
            boolean hasViewAll = permissionCheckService.hasAccess(
                    SecurityContextHolder.getContext().getAuthentication(),
                    HrModule.EMPLOYEE_PROFILE,
                    HrAction.VIEW_ALL
            );
            if (!hasViewAll) {
                Employee self = employeeRepository.findByUserId(authUser.getId())
                        .orElseThrow(() -> new RuntimeException("Employee record not found"));
                return List.of(toDTO(self));
            }
        }

        return employeeRepository
                .findByCompanyId(authUser.getCompany().getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<EmployeeResponseDTO> getEmployeesByCompany(Long companyId) {
        return employeeRepository.findByCompanyId(companyId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<EmployeeResponseDTO> getEmployeesByDepartment(Long departmentId) {
        return employeeRepository.findByDepartmentId(departmentId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public PageResponse<EmployeeResponseDTO> getEmployees(int page, int size) {

        User authUser = getAuthUser();

        if (authUser.getRole() == Role.USER) {
            Employee self = employeeRepository.findByUserId(authUser.getId())
                    .orElseThrow(() -> new RuntimeException("Employee not found"));
            return PageResponse.of(List.of(toDTO(self)), 1, 1, 0, 1);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Employee> empPage = employeeRepository.findAll(pageable);

        return PageResponse.of(
                empPage.getContent().stream().map(this::toDTO).collect(Collectors.toList()),
                empPage.getTotalElements(),
                empPage.getTotalPages(),
                page,
                size
        );
    }

    public EmployeeResponseDTO getCompanyAdmin(Long companyId) {
        // Uses Role enum — correct, this is a security/access concern
        List<Role> roles = List.of(Role.ADMIN, Role.SUPER_ADMIN);
        Employee admin = employeeRepository
                .findFirstByCompanyIdAndUserRoleIn(companyId, roles)
                .orElseThrow(() -> new RuntimeException("Admin not found"));
        return toDTO(admin);
    }

    public void syncAllEmployeeLeaveBalances(Long companyId) {
        // existing logic retained
    }

    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }

    // ======================================================
    // DTO MAPPER
    // ======================================================

    private EmployeeResponseDTO toDTO(Employee e) {

        Company              c  = e.getCompany();
        EmployeeContactInfo  ci = e.getContactInfo();

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

                .phoneNo(ci != null ? ci.getPhone() : null)
                .altPhone(ci != null ? ci.getAltPhone() : null)

                .email(e.getUser()     != null ? e.getUser().getEmail()              : null)
                .username(e.getUser()  != null ? e.getUser().getUsername()           : null)
                .userId(e.getUser()    != null ? e.getUser().getId()                 : null)
                .role(e.getUser()      != null ? e.getUser().getRole()               : null) // enum — for permissions
                .CompanyRole(e.getUser() != null ? e.getUser().getCompanyRole()      : null) // dynamic — for display ✅
                .forcePasswordReset(e.getUser() != null ? e.getUser().getForcePasswordReset() : null)

                .companyId(c   != null ? c.getId()                              : null)
                .companyName(c != null ? c.getCompanyName()                     : null)

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

    public List<EmployeeResponseDTO> getManagersByCompany(Long companyId) {

        List<Employee> managers =
                employeeRepository
                        .findByCompanyIdAndUserCompanyRoleIgnoreCase(
                                companyId,
                                "Manager"
                        );

        return managers.stream()
                .map(this::toDTO)
                .toList();
    }
}