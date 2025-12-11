package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.Role;
import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final AuthContext authContext;
    private final PasswordEncoder passwordEncoder;

    public EmployeeService(EmployeeRepository employeeRepository,
                           UserRepository userRepository,
                           CompanyRepository companyRepository,
                           DepartmentRepository departmentRepository,
                           AuthContext authContext,
                           PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.authContext = authContext;
        this.passwordEncoder = passwordEncoder;
    }


    // =====================================================================================
    // CREATE EMPLOYEE + USER
    // =====================================================================================
    public EmployeeResponseDTO createEmployee(CreateEmployeeDTO dto) {

        Company company = companyRepository.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Long currentUserId = authContext.getCurrentUserId();
        if (currentUserId == null || !company.getCreatedBy().equals(String.valueOf(currentUserId))) {
            throw new RuntimeException("You cannot add employees to another user's company");
        }

        Role role = dto.getRole() == null ? Role.USER : dto.getRole();

        if (role == Role.ADMIN) {
            if (employeeRepository.existsByCompanyIdAndUserRole(company.getId(), Role.ADMIN)) {
                throw new RuntimeException("This company already has an admin");
            }
        }

        Department dept = null;
        if (dto.getDepartmentId() != null) {
            dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        // Create User account
        if (dto.getUsername() == null || dto.getPassword() == null || dto.getEmail() == null) {
            throw new RuntimeException("username, password and email are required");
        }

        if (userRepository.existsByUsername(dto.getUsername()) || userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("username or email already exists");
        }

        User user = new User();
        user.setFullName(dto.getFirstName() + " " + dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(role);
        userRepository.save(user);

        Employee employee = Employee.builder()
                .employeeNo(dto.getEmployeeNo())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .phoneNo(dto.getPhoneNo())
                .company(company)
                .department(dept)
                .user(user)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return toDTO(employeeRepository.save(employee));
    }


    // =====================================================================================
    // GET EMPLOYEE BY ID
    // =====================================================================================
    public EmployeeResponseDTO getEmployeeById(Long id) {
        Employee e = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        return toDTO(e);
    }


    // =====================================================================================
    // GET EMPLOYEES BY COMPANY
    // =====================================================================================
    public List<EmployeeResponseDTO> getEmployeesByCompany(Long companyId) {

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Long userId = authContext.getCurrentUserId();
        if (!company.getCreatedBy().equals(String.valueOf(userId))) {
            throw new RuntimeException("You cannot access employees of another user’s company");
        }

        return employeeRepository.findByCompanyId(companyId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }


    // =====================================================================================
    // GET EMPLOYEES BY DEPARTMENT
    // =====================================================================================
    public List<EmployeeResponseDTO> getEmployeesByDepartment(Long departmentId) {
        return employeeRepository.findByDepartmentId(departmentId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }


    // =====================================================================================
    // DELETE EMPLOYEE
    // =====================================================================================
    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
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


    // =====================================================================================
    // DTO MAPPER
    // =====================================================================================
    private EmployeeResponseDTO toDTO(Employee e) {
        return EmployeeResponseDTO.builder()
                .id(e.getId())
                .employeeNo(e.getEmployeeNo())
                .firstName(e.getFirstName())
                .lastName(e.getLastName())
                .phoneNo(e.getPhoneNo())
                .companyId(e.getCompany() != null ? e.getCompany().getId() : null)
                .companyName(e.getCompany() != null ? e.getCompany().getCompanyName() : null)
                .departmentId(e.getDepartment() != null ? e.getDepartment().getId() : null)
                .departmentName(e.getDepartment() != null ? e.getDepartment().getDepartmentName() : null)
                .userId(e.getUser() != null ? e.getUser().getId() : null)
                .username(e.getUser() != null ? e.getUser().getUsername() : null)
                .email(e.getUser() != null ? e.getUser().getEmail() : null)
                .role(e.getUser() != null ? e.getUser().getRole() : null)
                .build();
    }
}
