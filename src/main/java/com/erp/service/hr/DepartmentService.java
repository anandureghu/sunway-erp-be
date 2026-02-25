package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.hr.CreateDepartmentDTO;
import com.erp.dto.hr.DepartmentResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthContext authContext;

    public DepartmentService(DepartmentRepository departmentRepository,
                             CompanyRepository companyRepository,
                             EmployeeRepository employeeRepository,
                             AuthContext authContext) {
        this.departmentRepository = departmentRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.authContext = authContext;
    }

    // ⭐ Convert Entity → DTO
    private DepartmentResponseDTO toDTO(Department d) {
        return DepartmentResponseDTO.builder()
                .id(d.getId())
                .departmentCode(d.getDepartmentCode())
                .departmentName(d.getDepartmentName())
                .description(d.getDescription())

                .managerId(d.getManager() != null ? d.getManager().getId() : null)
                .managerFirstName(d.getManager() != null ? d.getManager().getFirstName() : null)
                .managerLastName(d.getManager() != null ? d.getManager().getLastName() : null)

                .companyId(d.getCompany().getId())
                .companyName(d.getCompany().getCompanyName())
                .companyCode(d.getCompany().getCompanyCode())
                .build();
    }

    public List<DepartmentResponseDTO> getDepartmentsForCurrentUser() {
        Long companyId = authContext.getCurrentCompanyId();

        return departmentRepository.findAllByCompanyId(companyId)
                .stream().map(this::toDTO).toList();
    }

    public DepartmentResponseDTO getDepartmentById(Long id) {
        return departmentRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Department not found"));
    }

    public DepartmentResponseDTO createDepartment(CreateDepartmentDTO dto) {
        Company company = companyRepository.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Employee manager = null;
        if (dto.getManagerId() != null) {
            manager = employeeRepository.findById(dto.getManagerId())
                    .orElseThrow(() -> new RuntimeException("Manager not found"));
        }

        Department department = Department.builder()
                .departmentCode(dto.getDepartmentCode())
                .departmentName(dto.getDepartmentName())
                .manager(manager)
                .company(company)
                .description(dto.getDescription())
                .createdAt(Instant.now())
                .build();

        return toDTO(departmentRepository.save(department));
    }

    public List<DepartmentResponseDTO> getDepartmentsByCompanyId(Long companyId) {
        return departmentRepository.findAllByCompanyId(companyId)
                .stream().map(this::toDTO).toList();
    }

    public void deleteDepartment(Long id) {
        departmentRepository.deleteById(id);
    }
}
