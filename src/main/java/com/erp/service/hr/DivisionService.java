package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Division;
import com.erp.dto.hr.CreateDivisionDTO;
import com.erp.dto.hr.DivisionResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DivisionRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class DivisionService {
    private final DivisionRepository divisionRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthContext authContext;

    public DivisionService(DivisionRepository divisionRepository,
                           CompanyRepository companyRepository,
                           EmployeeRepository employeeRepository,
                           AuthContext authContext) {
        this.divisionRepository = divisionRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.authContext = authContext;
    }

    private DivisionResponseDTO toDTO(Division d) {
        return DivisionResponseDTO.builder()
                .id(d.getId())
                .code(d.getCode())
                .name(d.getName())
                .description(d.getDescription())
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
        return divisionRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().map(this::toDTO).toList();
    }

    public DivisionResponseDTO getDivisionById(Long id) {
        return divisionRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new RuntimeException("Division not found"));
    }

    public DivisionResponseDTO createDivision(CreateDivisionDTO dto) {
        Long companyId = dto.getCompanyId() != null
                ? dto.getCompanyId()
                : authContext.getCurrentCompanyId();

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        Employee manager = null;
        if (dto.getManagerId() != null) {
            manager = employeeRepository.findById(dto.getManagerId())
                    .orElseThrow(() -> new RuntimeException("Manager not found"));
        }

        Division division = Division.builder()
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
        return divisionRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().map(this::toDTO).toList();
    }

    public void deleteDivision(Long id) {
        divisionRepository.deleteById(id);
    }
}
