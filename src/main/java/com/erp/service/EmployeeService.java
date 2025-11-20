package com.erp.service;

import com.erp.domain.Role;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.domain.Employee;
import com.erp.dto.hr.CreateEmployeeDTO;
import com.erp.dto.hr.EmployeeResponseDTO;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final AuthContext authContext;

    public EmployeeService(EmployeeRepository employeeRepository,
                           CompanyRepository companyRepository,
                           DepartmentRepository departmentRepository,
                           AuthContext authContext) {
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.departmentRepository = departmentRepository;
        this.authContext = authContext;
    }

    // 🔄 Convert Entity → DTO
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
                .build();
    }
    public EmployeeResponseDTO createEmployee(CreateEmployeeDTO dto) {

        // Validate company
        Company company = companyRepository.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // Ensure user owns company
        Long userId = authContext.getCurrentUserId();
        if (!company.getCreatedBy().equals(String.valueOf(userId))) {
            throw new RuntimeException("You cannot add employees to another user's company");
        }

        // ⭐ Default role if not sent
        Role role = dto.getRole() == null ? Role.USER : dto.getRole();

        // ⭐ If ADMIN → ensure permissions
        if (role == Role.ADMIN) {

            // Only company owner can create admin
            if (!company.getCreatedBy().equals(String.valueOf(userId))) {
                throw new RuntimeException("Only the company owner can create an admin");
            }

            // Optional rule: Only one admin per company
            boolean adminExists = employeeRepository.existsByCompanyIdAndRole(company.getId(), Role.ADMIN);
            if (adminExists) {
                throw new RuntimeException("This company already has an admin");
            }
        }

        // Validate department (optional)
        Department dept = null;
        if (dto.getDepartmentId() != null) {
            dept = departmentRepository.findById(dto.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found"));
        }

        Employee employee = Employee.builder()
                .employeeNo(dto.getEmployeeNo())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .phoneNo(dto.getPhoneNo())
                .company(company)
                .department(dept)
                .role(role) // ⭐ NEW
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return toDTO(employeeRepository.save(employee));
    }


    public List<EmployeeResponseDTO> getEmployeesByCompany(Long companyId) {
        return employeeRepository.findByCompanyId(companyId)
                .stream().map(this::toDTO).toList();
    }

    public List<EmployeeResponseDTO> getEmployeesByDepartment(Long departmentId) {
        return employeeRepository.findByDepartmentId(departmentId)
                .stream().map(this::toDTO).toList();
    }

    public EmployeeResponseDTO getEmployeeById(Long id) {
        return employeeRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
    }

    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }

    public EmployeeResponseDTO getCompanyAdmin(Long companyId) {

        // Ensure current user owns this company
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Long userId = authContext.getCurrentUserId();
        if (!company.getCreatedBy().equals(String.valueOf(userId))) {
            throw new RuntimeException("You cannot access employees of another user's company");
        }

        // Load admin
        Employee admin = employeeRepository
                .findByCompany_IdAndRole(companyId, Role.ADMIN)
                .orElseThrow(() -> new RuntimeException("No admin exists for this company"));

        return toDTO(admin);
    }


}
