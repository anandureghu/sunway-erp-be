package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.domain.hr.Division;
import com.erp.dto.hr.CreateDivisionDTO;
import com.erp.dto.hr.DivisionResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.repo.hr.DivisionRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DivisionService {
    private final DepartmentRepository departmentRepository;
    private final DivisionRepository divisionRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthContext authContext;

    public DivisionService(DepartmentRepository departmentRepository, DivisionRepository divisionRepository,
                           CompanyRepository companyRepository,
                           EmployeeRepository employeeRepository,
                           AuthContext authContext) {
        this.departmentRepository = departmentRepository;
        this.divisionRepository = divisionRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.authContext = authContext;
    }

    // ⭐ Convert Entity → DTO
    private DivisionResponseDTO toDTO(Division d) {
        return DivisionResponseDTO.builder()
                .id(d.getId())
                .description(d.getDescription())
                .code(d.getCode())
                .name(d.getName())

                .departmentId(d.getDepartment().getId())
                .departmentCode(d.getDepartment().getDepartmentCode())
                .departmentName(d.getDepartment().getDepartmentName())

                .managerId(d.getManager() != null ? d.getManager().getId() : null)
                .managerFirstName(d.getManager() != null ? d.getManager().getFirstName() : null)
                .managerLastName(d.getManager() != null ? d.getManager().getLastName() : null)

                .companyId(d.getCompany().getId())
                .companyName(d.getCompany().getCompanyName())
                .companyCode(d.getCompany().getCompanyCode())
                .build();
    }

    public List<DivisionResponseDTO> getDivisionsForCurrentUser() {
        Long companyId = authContext.getCurrentCompanyId();
        return divisionRepository.findAllByCompanyId(companyId)
                .stream().map(this::toDTO).toList();
    }

    public DivisionResponseDTO getDivisionById(Long id) {
        return divisionRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Division not found"));
    }

    public DivisionResponseDTO createDivision(CreateDivisionDTO dto) {
        Company company = companyRepository.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Department department = departmentRepository.findById(dto.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Department not found"));

        Employee manager = null;
        if (dto.getManagerId() != null) {
            manager = employeeRepository.findById(dto.getManagerId())
                    .orElseThrow(() -> new RuntimeException("Manager not found"));
        }

        Division division = Division.builder()
                .department(department)
                .company(company)
                .manager(manager)
                .description(dto.getDescription())
                .code(dto.getCode())
                .name(dto.getName())
                .createdAt(Instant.now())
                .build();


        return toDTO(divisionRepository.save(division));
    }

    public List<DivisionResponseDTO> getDivisionsByCompanyId(Long companyId) {
        return divisionRepository.findAllByCompanyId(companyId)
                .stream().map(this::toDTO).toList();
    }

    public void deleteDivision(Long id) {
        divisionRepository.deleteById(id);
    }
}
