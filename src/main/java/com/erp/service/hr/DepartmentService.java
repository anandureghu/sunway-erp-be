package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.hr.CreateDepartmentDTO;
import com.erp.dto.hr.DepartmentResponseDTO;
import com.erp.exception.NotFoundException;
import com.erp.exception.ConflictException;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.security.context.AuthContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final CompanyRepository    companyRepository;
    private final EmployeeRepository   employeeRepository;
    private final AuthContext          authContext;
    private final EmployeeCurrentJobRepo employeeCurrentJobRepository;

    // ── Create ────────────────────────────────────────────────────────────────

    public DepartmentResponseDTO createDepartment(Long companyId, CreateDepartmentDTO dto) {

        Company company = resolveCompany(companyId);

        if (departmentRepository.existsByDepartmentCodeAndCompanyId(
                dto.getDepartmentCode(), company.getId())) {
            throw new ConflictException("Department code already exists in this company");
        }

        // Manager is optional; validate it only when provided.
        Employee manager = resolveManager(dto.getManagerId(), company.getId());

        Department department = Department.builder()
                .departmentCode(dto.getDepartmentCode())
                .departmentName(dto.getDepartmentName())
                .description(dto.getDescription())
                .manager(manager)
                .company(company)
                .createdAt(Instant.now())
                .build();

        return toDTO(departmentRepository.save(department));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    public DepartmentResponseDTO updateDepartment(Long companyId, Long id, CreateDepartmentDTO dto) {

        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Department not found"));

        Company company = resolveCompany(companyId);

        if (!department.getCompany().getId().equals(company.getId())) {
            throw new ConflictException("Department does not belong to this company");
        }

        if (!department.getDepartmentCode().equals(dto.getDepartmentCode()) &&
                departmentRepository.existsByDepartmentCodeAndCompanyId(
                        dto.getDepartmentCode(), company.getId())) {
            throw new ConflictException("Department code already exists in this company");
        }

        department.setDepartmentCode(dto.getDepartmentCode());
        department.setDepartmentName(dto.getDepartmentName());
        department.setDescription(dto.getDescription());
        department.setManager(resolveManager(dto.getManagerId(), company.getId()));

        return toDTO(departmentRepository.save(department));
    }

    // ── Get All ───────────────────────────────────────────────────────────────

    public List<DepartmentResponseDTO> getDepartmentsByCompanyId(Long companyId) {
        return departmentRepository.findAllByCompanyIdOrderByCreatedAtDesc(resolveCompany(companyId).getId())
                .stream().map(this::toDTO).toList();
    }

    // ── Get Single ────────────────────────────────────────────────────────────

    public DepartmentResponseDTO getDepartmentById(Long companyId, Long id) {

        Company    company    = resolveCompany(companyId);
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Department not found"));

        if (!department.getCompany().getId().equals(company.getId())) {
            throw new ConflictException("Access denied for this department");
        }

        return toDTO(department);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void deleteDepartment(Long companyId, Long id) {

        Company company = resolveCompany(companyId);

        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Department not found"));

        if (!department.getCompany().getId().equals(company.getId())) {
            throw new ConflictException("Access denied for this department");
        }

        if (employeeCurrentJobRepository.existsByDepartment_Id(id)) {
            throw new ConflictException(
                    "Cannot delete department because employees are assigned to it."
            );
        }

        departmentRepository.delete(department);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company resolveCompany(Long pathCompanyId) {

        String role          = authContext.getCurrentUserRole();
        Long   userCompanyId = authContext.getCurrentCompanyId();

        if ("SUPER_ADMIN".equals(role)) {
            if (pathCompanyId == null)
                throw new ConflictException("Company must be selected");
            return companyRepository.findById(pathCompanyId)
                    .orElseThrow(() -> new NotFoundException("Company not found"));
        }

        if (userCompanyId == null)
            throw new ConflictException("User not associated with any company");

        return companyRepository.findById(userCompanyId)
                .orElseThrow(() -> new NotFoundException("Company not found"));
    }

    /**
     * Manager is optional. When provided, validates the manager belongs to the same company.
     */
    private Employee resolveManager(Long managerId, Long companyId) {

        if (managerId == null) {
            return null;
        }

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new NotFoundException("Manager not found"));

        if (!manager.getCompany().getId().equals(companyId)) {
            throw new ConflictException("Manager must belong to the same company");
        }

        return manager;
    }

    private DepartmentResponseDTO toDTO(Department d) {
        return DepartmentResponseDTO.builder()
                .id(d.getId())
                .departmentCode(d.getDepartmentCode())
                .departmentName(d.getDepartmentName())
                .description(d.getDescription())
                .managerId(d.getManager() != null ? d.getManager().getId()        : null)
                .managerFirstName(d.getManager() != null ? d.getManager().getFirstName() : null)
                .managerLastName(d.getManager()  != null ? d.getManager().getLastName()  : null)
                .companyId(d.getCompany().getId())
                .companyName(d.getCompany().getCompanyName())
                .build();
    }
}
