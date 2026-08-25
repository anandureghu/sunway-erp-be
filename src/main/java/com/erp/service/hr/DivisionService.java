package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.domain.hr.Division;
import com.erp.dto.hr.CreateDivisionDTO;
import com.erp.dto.hr.DivisionResponseDTO;
import com.erp.exception.ConflictException;
import com.erp.exception.NotFoundException;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.repo.hr.DivisionRepository;
import com.erp.security.context.AuthContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DivisionService {
    private final DivisionRepository divisionRepository;
    private final CompanyRepository companyRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthContext authContext;

    private DivisionResponseDTO toDTO(Division d) {
        Department department = d.getDepartment();
        return DivisionResponseDTO.builder()
                .id(d.getId())
                .code(d.getCode())
                .name(d.getName())
                .description(d.getDescription())
                .managerId(d.getManager() != null ? d.getManager().getId() : null)
                .managerFirstName(d.getManager() != null ? d.getManager().getFirstName() : null)
                .managerLastName(d.getManager() != null ? d.getManager().getLastName() : null)
                .departmentId(department != null ? department.getId() : null)
                .departmentCode(department != null ? department.getDepartmentCode() : null)
                .departmentName(department != null ? department.getDepartmentName() : null)
                .companyId(d.getCompany().getId())
                .companyName(d.getCompany().getCompanyName())
                .companyCode(d.getCompany().getCompanyCode())
                .build();
    }

    public List<DivisionResponseDTO> getDivisionsForCurrentUser() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            return List.of();
        }
        return getDivisionsByCompanyId(companyId);
    }

    public DivisionResponseDTO getDivisionById(Long id) {
        Division division = divisionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Division not found"));
        assertCompanyAccess(division.getCompany().getId());
        return toDTO(division);
    }

    public DivisionResponseDTO createDivision(CreateDivisionDTO dto) {
        Long companyId = dto.getCompanyId() != null
                ? dto.getCompanyId()
                : authContext.getCurrentCompanyId();

        Company company = resolveCompany(companyId);
        Employee manager = resolveManager(dto.getManagerId(), company.getId());
        Department department = resolveDepartment(dto.getDepartmentId(), company.getId());

        Division division = Division.builder()
                .company(company)
                .department(department)
                .manager(manager)
                .description(dto.getDescription())
                .code(dto.getCode())
                .name(dto.getName())
                .createdAt(Instant.now())
                .build();

        return toDTO(divisionRepository.save(division));
    }

    public DivisionResponseDTO updateDivision(Long id, CreateDivisionDTO dto) {
        Division division = divisionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Division not found"));

        assertCompanyAccess(division.getCompany().getId());

        Long companyId = division.getCompany().getId();

        if (dto.getCode() != null) {
            division.setCode(dto.getCode());
        }
        if (dto.getName() != null) {
            division.setName(dto.getName());
        }
        division.setDescription(dto.getDescription());
        division.setManager(resolveManager(dto.getManagerId(), companyId));
        division.setDepartment(resolveDepartment(dto.getDepartmentId(), companyId));

        if (dto.getCompanyId() != null
                && !dto.getCompanyId().equals(division.getCompany().getId())) {
            Company company = resolveCompany(dto.getCompanyId());
            division.setCompany(company);
        }

        return toDTO(divisionRepository.save(division));
    }

    public List<DivisionResponseDTO> getDivisionsByCompanyId(Long companyId) {
        resolveCompany(companyId);
        return divisionRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().map(this::toDTO).toList();
    }

    public List<DivisionResponseDTO> getDivisionsByDepartmentId(Long departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new NotFoundException("Department not found"));
        assertCompanyAccess(department.getCompany().getId());
        return divisionRepository.findAllByDepartment_IdOrderByCreatedAtDesc(departmentId)
                .stream().map(this::toDTO).toList();
    }

    public void deleteDivision(Long id) {
        Division division = divisionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Division not found"));
        assertCompanyAccess(division.getCompany().getId());
        divisionRepository.delete(division);
    }

    private Company resolveCompany(Long companyId) {
        String role = authContext.getCurrentUserRole();
        Long userCompanyId = authContext.getCurrentCompanyId();

        if ("SUPER_ADMIN".equals(role)) {
            if (companyId == null) {
                throw new ConflictException("Company must be selected");
            }
            return companyRepository.findById(companyId)
                    .orElseThrow(() -> new NotFoundException("Company not found"));
        }

        if (userCompanyId == null) {
            throw new ConflictException("User not associated with any company");
        }

        if (!userCompanyId.equals(companyId)) {
            throw new ConflictException("Access denied for this company");
        }

        return companyRepository.findById(userCompanyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));
    }

    private void assertCompanyAccess(Long companyId) {
        String role = authContext.getCurrentUserRole();
        if ("SUPER_ADMIN".equals(role)) {
            return;
        }
        Long userCompanyId = authContext.getCurrentCompanyId();
        if (userCompanyId == null || !userCompanyId.equals(companyId)) {
            throw new ConflictException("Access denied for this company");
        }
    }

    private Employee resolveManager(Long managerId, Long companyId) {
        if (managerId == null) {
            return null;
        }

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new NotFoundException("Manager not found"));

        if (!manager.getCompany().getId().equals(companyId)) {
            throw new ConflictException("Manager must belong to the same company");
        }

        if (manager.getStatus() != null && manager.getStatus().isDepartedOrInactive()) {
            throw new ConflictException(
                    "A terminated, resigned, retired or inactive employee cannot be a division manager.");
        }

        return manager;
    }

    private Department resolveDepartment(Long departmentId, Long companyId) {
        if (departmentId == null) {
            return null;
        }

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new NotFoundException("Department not found"));

        if (!department.getCompany().getId().equals(companyId)) {
            throw new ConflictException("Department must belong to the same company");
        }

        return department;
    }
}
