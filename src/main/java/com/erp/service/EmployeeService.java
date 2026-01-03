package com.erp.service;

import com.erp.domain.*;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.common.PageResponse;
import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.dto.hr.UpdateEmployeeDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.repo.contact.EmployeeContactInfoRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeContactInfoRepository contactInfoRepository;
    private final AuthContext authContext;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(
            EmployeeRepository employeeRepository,
            UserRepository userRepository,
            CompanyRepository companyRepository,
            DepartmentRepository departmentRepository,
            EmployeeContactInfoRepository contactInfoRepository,
            AuthContext authContext,
            PasswordEncoder passwordEncoder
    ) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.contactInfoRepository = contactInfoRepository;
        this.authContext = authContext;
        this.passwordEncoder = passwordEncoder;
    }

    // ======================================================
    // CREATE EMPLOYEE
    // ======================================================
    public EmployeeResponseDTO createEmployee(CreateEmployeeDTO dto) {

        User authUser = getAuthUser();

        Company company = authUser.getRole() == Role.SUPER_ADMIN
                ? companyRepository.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"))
                : authUser.getCompany();

        if (company == null) {
            throw new RuntimeException("Company is required");
        }

        Role role = dto.getRole() == null ? Role.USER : dto.getRole();

        if (role == Role.ADMIN &&
                employeeRepository.existsByCompanyIdAndUserRole(company.getId(), Role.ADMIN)) {
            throw new RuntimeException("This company already has an admin");
        }

        Department department = null;
        if (dto.getDepartmentId() != null) {
            department = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));

            if (!department.getCompany().getId().equals(company.getId())) {
                throw new RuntimeException("Department does not belong to this company");
            }
        }

        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        User user = new User();
        user.setFullName(dto.getFirstName() + " " + dto.getLastName());
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(role);
        user.setCompany(company);
        userRepository.save(user);

        Employee employee = Employee.builder()
                .employeeNo(dto.getEmployeeNo())
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

        employee = employeeRepository.save(employee);

        EmployeeContactInfo contactInfo = EmployeeContactInfo.builder()
                .employee(employee)
                .email(dto.getEmail())
                .phone(dto.getPhoneNo())
                .altPhone(dto.getAltPhone())
                .build();

        contactInfoRepository.save(contactInfo);

        return toDTO(employee);
    }

    // ======================================================
    // UPDATE EMPLOYEE
    // ======================================================
    public EmployeeResponseDTO updateEmployee(Long employeeId, UpdateEmployeeDTO dto) {

        User authUser = getAuthUserWithCompany();

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (!employee.getCompany().getId().equals(authUser.getCompany().getId())) {
            throw new RuntimeException("Access denied");
        }

        if (dto.getEmployeeNo() != null) employee.setEmployeeNo(dto.getEmployeeNo());
        if (dto.getFirstName() != null) employee.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) employee.setLastName(dto.getLastName());
        if (dto.getGender() != null) employee.setGender(dto.getGender());
        if (dto.getPrefix() != null) employee.setPrefix(dto.getPrefix());
        if (dto.getMaritalStatus() != null) employee.setMaritalStatus(dto.getMaritalStatus());
        if (dto.getStatus() != null) employee.setStatus(dto.getStatus());
        if (dto.getDateOfBirth() != null) employee.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getJoinDate() != null) employee.setJoinDate(dto.getJoinDate());
        if (dto.getNotes() != null) employee.setNotes(dto.getNotes());

        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));

            if (!dept.getCompany().getId().equals(employee.getCompany().getId())) {
                throw new RuntimeException("Department does not belong to this company");
            }
            employee.setDepartment(dept);
        }

        EmployeeContactInfo contactInfo = contactInfoRepository
                .findByEmployeeId(employeeId)
                .orElseGet(() -> EmployeeContactInfo.builder()
                        .employee(employee)
                        .build()
                );

        if (dto.getPhoneNo() != null) contactInfo.setPhone(dto.getPhoneNo());
        if (dto.getAltPhone() != null) contactInfo.setAltPhone(dto.getAltPhone());
        if (dto.getEmail() != null) contactInfo.setEmail(dto.getEmail());

        contactInfoRepository.save(contactInfo);

        if (dto.getEmail() != null && employee.getUser() != null) {
            if (!dto.getEmail().equals(employee.getUser().getEmail()) &&
                    userRepository.existsByEmail(dto.getEmail())) {
                throw new RuntimeException("Email already exists");
            }
            employee.getUser().setEmail(dto.getEmail());
            userRepository.save(employee.getUser());
        }

        employee.setUpdatedAt(Instant.now());
        employeeRepository.save(employee);

        return toDTO(employee);
    }

    // ======================================================
    // GET EMPLOYEES
    // ======================================================
    public List<EmployeeResponseDTO> getEmployees() {
        User authUser = getAuthUserWithCompany();
        return employeeRepository.findByCompanyId(authUser.getCompany().getId())
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public PageResponse<EmployeeResponseDTO> getEmployees(int page, int size) {
        User authUser = getAuthUserWithCompany();
        Pageable pageable = PageRequest.of(page, size);

        Page<Employee> empPage =
                employeeRepository.findByCompanyId(authUser.getCompany().getId(), pageable);

        return PageResponse.of(
                empPage.getContent().stream().map(this::toDTO).collect(Collectors.toList()),
                empPage.getTotalElements(),
                empPage.getTotalPages(),
                page,
                size
        );
    }

    public EmployeeResponseDTO getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        User authUser = getAuthUserWithCompany();
        if (!employee.getCompany().getId().equals(authUser.getCompany().getId())) {
            throw new RuntimeException("Access denied");
        }

        return toDTO(employee);
    }

    public List<EmployeeResponseDTO> getEmployeesByDepartment(Long departmentId) {
        User authUser = getAuthUserWithCompany();

        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        if (!dept.getCompany().getId().equals(authUser.getCompany().getId())) {
            throw new RuntimeException("Access denied");
        }

        return employeeRepository.findByDepartmentId(departmentId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<EmployeeResponseDTO> getEmployeesByCompany(Long companyId) {
        User authUser = getAuthUser();

        if (authUser.getRole() != Role.SUPER_ADMIN &&
                !authUser.getCompany().getId().equals(companyId)) {
            throw new RuntimeException("Access denied");
        }

        return employeeRepository.findByCompanyId(companyId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ======================================================
    // DELETE
    // ======================================================
    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }

    // ======================================================
    // HELPERS
    // ======================================================
    private User getAuthUser() {
        Long userId = authContext.getCurrentUserId();
        if (userId == null) throw new RuntimeException("Unauthorized");
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // =====================================================================================
    // GET COMPANY ADMIN
    // =====================================================================================
    public EmployeeResponseDTO getCompanyAdmin(Long companyId) {

        List<Role> allowedRoles = List.of(Role.ADMIN, Role.SUPER_ADMIN);

        Employee admin = employeeRepository
                .findByCompanyIdAndUserRoleIn(companyId, allowedRoles)
                .orElseThrow(() -> new RuntimeException("No admin found for this company"));

        return toDTO(admin);
    }

    private User getAuthUserWithCompany() {
        User user = getAuthUser();
        if (user.getCompany() == null) {
            throw new RuntimeException("User is not linked to any company");
        }
        return user;
    }

    // ======================================================
    // DTO MAPPER
    // ======================================================
    private EmployeeResponseDTO toDTO(Employee e) {

        Company c = e.getCompany();
        EmployeeContactInfo ci = e.getContactInfo();

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
                .build();
    }

    public EmployeeResponseDTO uploadImage(Long employeeId, MultipartFile file) {
        Employee emp = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (file.isEmpty()) {
            throw new RuntimeException("Empty file");
        }

        try {
            // Create directory if not exists
            String uploadDir = "uploads/employees/";
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();

            // Generate file name
            String ext = Objects.requireNonNull(file.getOriginalFilename())
                    .substring(file.getOriginalFilename().lastIndexOf("."));
            String fileName = "employee_" + employeeId + ext;

            Path filePath = Paths.get(uploadDir + fileName);

            // Save file to server
            Files.write(filePath, file.getBytes());

            // Set URL to entity
            emp.setImageUrl("/uploads/employees/" + fileName);
            employeeRepository.save(emp);

            return toDTO(emp);

        } catch (Exception e) {
            throw new RuntimeException("Failed to save image", e);
        }
    }

}
