package com.erp.service;

import com.erp.domain.*;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
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
import com.erp.util.EmployeeUserUtil;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final LeavePolicyService leavePolicyService; // ✅ Added
    private final AuthContext authContext;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            UserRepository userRepository,
            CompanyRepository companyRepository,
            DepartmentRepository departmentRepository,
            EmployeeContactInfoRepository contactInfoRepository,
            CompanyLeavePolicyRepository policyRepo,
            EmployeeLeaveBalanceRepository balanceRepo,
            LeavePolicyService leavePolicyService, // ✅ Added
            AuthContext authContext,
            PasswordEncoder passwordEncoder,
            FileStorageService fileStorageService
    ) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.contactInfoRepository = contactInfoRepository;
        this.policyRepo = policyRepo;
        this.balanceRepo = balanceRepo;
        this.leavePolicyService = leavePolicyService; // ✅ Added
        this.authContext = authContext;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
    }

    // ======================================================
    // CREATE EMPLOYEE
    // ======================================================
    @Transactional
    public EmployeeResponseDTO createEmployee(@Valid CreateEmployeeDTO dto) {

        User authUser = getAuthUser();

        Company company = authUser.getRole() == Role.SUPER_ADMIN
                ? companyRepository.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"))
                : authUser.getCompany();

        Role role = dto.getRole() == null ? Role.USER : dto.getRole();

        Department department = null;
        if (dto.getDepartmentId() != null) {
            department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        employeeRepository.incrementEmployeeNo();
        String employeeNo = String.valueOf(employeeRepository.getCurrentEmployeeNo());

        String username = EmployeeUserUtil.generateUsername(
                dto.getFirstName(),
                dto.getLastName()
        );

        String email = EmployeeUserUtil.generateEmail(username, company);
        String rawPassword = EmployeeUserUtil.generateDefaultPassword(username);

        User user = new User();
        user.setFullName(dto.getFirstName() + " " + dto.getLastName());
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setCompany(company);
        user.setForcePasswordReset(true);
        userRepository.save(user);

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
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        employeeRepository.save(employee);

        // ✅ Auto initialize leave balances
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

        if (dto.getFirstName() != null) employee.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) employee.setLastName(dto.getLastName());
        if (dto.getGender() != null) employee.setGender(dto.getGender());
        if (dto.getStatus() != null) employee.setStatus(dto.getStatus());

        employee.setUpdatedAt(Instant.now());
        employeeRepository.save(employee);

        return toDTO(employee);
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
                    image,
                    FileCategory.EMPLOYEE_PROFILE,
                    id.toString(),
                    true
            );

            employee.setImageUrl(upload.getBlobPath());
            employeeRepository.save(employee);
        }

        return toDTO(employee);
    }

    // ======================================================
    // GET METHODS
    // ======================================================

    public EmployeeResponseDTO getEmployeeById(Long id) {
        return toDTO(employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found")));
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

        List<Role> roles = List.of(Role.ADMIN, Role.SUPER_ADMIN);

        Employee admin = employeeRepository
                .findByCompanyIdAndUserRoleIn(companyId, roles)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        return toDTO(admin);
    }

    public void syncAllEmployeeLeaveBalances(Long companyId) {
        // Keep existing logic if needed
    }

    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }

    public List<EmployeeResponseDTO> getEmployees() {

        User authUser = getAuthUser();

        if (authUser.getCompany() == null) {
            throw new RuntimeException("User not linked to any company");
        }

        return employeeRepository
                .findByCompanyId(authUser.getCompany().getId())
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ======================================================
    // DTO MAPPER
    // ======================================================
    private EmployeeResponseDTO toDTO(Employee e) {

        Company c = e.getCompany();
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
                .status(e.getStatus().name())
                .maritalStatus(e.getMaritalStatus())
                .dateOfBirth(e.getDateOfBirth())
                .joinDate(e.getJoinDate())
                .phoneNo(ci != null ? ci.getPhone() : null)
                .altPhone(ci != null ? ci.getAltPhone() : null)
                .email(e.getUser() != null ? e.getUser().getEmail() : null)
                .notes(e.getNotes())
                .companyId(c != null ? c.getId() : null)
                .companyName(c != null ? c.getCompanyName() : null)
                .departmentId(e.getDepartment() != null ? e.getDepartment().getId() : null)
                .departmentName(e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null)
                .userId(e.getUser() != null ? e.getUser().getId() : null)
                .username(e.getUser() != null ? e.getUser().getUsername() : null)
                .role(e.getUser() != null ? e.getUser().getRole() : null)
                .forcePasswordReset(e.getUser() != null ? e.getUser().getForcePasswordReset() : null)
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
}
