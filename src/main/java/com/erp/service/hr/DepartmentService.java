package com.erp.service.hr;

import com.erp.domain.Employee;
import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.hr.CreateDepartmentDTO;
import com.erp.dto.hr.DepartmentResponseDTO;
import com.erp.exception.NotFoundException;
import com.erp.exception.ConflictException;
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
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthContext authContext;

    // ============================================================
    // ROLE BASED FETCH
    // ============================================================

    public List<DepartmentResponseDTO> getDepartmentsForCurrentUser() {

        String role = authContext.getCurrentUserRole();
        Long companyId = authContext.getCurrentCompanyId();

        if ("SUPER_ADMIN".equals(role)) {
            return departmentRepository.findAll()
                    .stream()
                    .map(this::toDTO)
                    .toList();
        }

        if (companyId == null) {
            throw new NotFoundException("Company not associated with user");
        }

        return departmentRepository.findAllByCompanyId(companyId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ============================================================
    // GET BY ID (Role Safe)
    // ============================================================

    public DepartmentResponseDTO getDepartmentById(Long id) {

        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Department not found"));

        validateAccess(department.getCompany().getId());

        return toDTO(department);
    }

    // ============================================================
    // CREATE
    // ============================================================

    public DepartmentResponseDTO createDepartment(CreateDepartmentDTO dto) {

        String role = authContext.getCurrentUserRole();
        Long userCompanyId = authContext.getCurrentCompanyId();

        Company company;

        if ("SUPER_ADMIN".equals(role)) {
            company = companyRepository.findById(dto.getCompanyId())
                    .orElseThrow(() -> new NotFoundException("Company not found"));
        } else {

            if (userCompanyId == null || !dto.getCompanyId().equals(userCompanyId)) {
                throw new ConflictException("You can only create departments for your company");
            }

            company = companyRepository.findById(userCompanyId)
                    .orElseThrow(() -> new NotFoundException("Company not found"));
        }

        // Prevent duplicate department code within same company
        if (departmentRepository.existsByDepartmentCodeAndCompanyId(
                dto.getDepartmentCode(), company.getId())) {
            throw new ConflictException("Department code already exists in this company");
        }

        Employee manager = null;
        if (dto.getManagerId() != null) {

            manager = employeeRepository.findById(dto.getManagerId())
                    .orElseThrow(() -> new NotFoundException("Manager not found"));

            // Ensure manager belongs to same company
            if (!manager.getCompany().getId().equals(company.getId())) {
                throw new ConflictException("Manager must belong to same company");
            }
        }

        Department department = Department.builder()
                .departmentCode(dto.getDepartmentCode())
                .departmentName(dto.getDepartmentName())
                .manager(manager)
                .company(company)
                .createdAt(Instant.now())
                .build();

        return toDTO(departmentRepository.save(department));
    }

    // ============================================================
    // DELETE (Role Safe)
    // ============================================================

    public void deleteDepartment(Long id) {

        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Department not found"));

        validateAccess(department.getCompany().getId());

        departmentRepository.delete(department);
    }

    // ============================================================
    // PRIVATE HELPERS
    // ============================================================

    private void validateAccess(Long departmentCompanyId) {

        String role = authContext.getCurrentUserRole();

        if ("SUPER_ADMIN".equals(role)) {
            return;
        }

        Long userCompanyId = authContext.getCurrentCompanyId();

        if (userCompanyId == null || !userCompanyId.equals(departmentCompanyId)) {
            throw new ConflictException("Access denied for this department");
        }
    }

    private DepartmentResponseDTO toDTO(Department d) {
        return DepartmentResponseDTO.builder()
                .id(d.getId())
                .departmentCode(d.getDepartmentCode())
                .departmentName(d.getDepartmentName())

                .managerId(d.getManager() != null ? d.getManager().getId() : null)
                .managerFirstName(d.getManager() != null ? d.getManager().getFirstName() : null)
                .managerLastName(d.getManager() != null ? d.getManager().getLastName() : null)

                .companyId(d.getCompany().getId())
                .companyName(d.getCompany().getCompanyName())
                .build();
    }

    // ============================================================
// UPDATE
// ============================================================

    public DepartmentResponseDTO updateDepartment(Long id, CreateDepartmentDTO dto) {

        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Department not found"));

        validateAccess(department.getCompany().getId());

        // Prevent duplicate code in same company
        if (!department.getDepartmentCode().equals(dto.getDepartmentCode()) &&
                departmentRepository.existsByDepartmentCodeAndCompanyId(
                        dto.getDepartmentCode(),
                        department.getCompany().getId())) {

            throw new ConflictException("Department code already exists in this company");
        }

        department.setDepartmentCode(dto.getDepartmentCode());
        department.setDepartmentName(dto.getDepartmentName());

        if (dto.getManagerId() != null) {
            Employee manager = employeeRepository.findById(dto.getManagerId())
                    .orElseThrow(() -> new NotFoundException("Manager not found"));

            if (!manager.getCompany().getId().equals(department.getCompany().getId())) {
                throw new ConflictException("Manager must belong to same company");
            }

            department.setManager(manager);
        } else {
            department.setManager(null);
        }

        return toDTO(departmentRepository.save(department));
    }

    // ============================================================
// GET BY COMPANY ID (Role Safe)
// ============================================================

    public List<DepartmentResponseDTO> getDepartmentsByCompanyId(Long companyId) {

        String role = authContext.getCurrentUserRole();
        Long userCompanyId = authContext.getCurrentCompanyId();

        // SUPER_ADMIN can fetch any company
        if ("SUPER_ADMIN".equals(role)) {
            return departmentRepository.findAllByCompanyId(companyId)
                    .stream()
                    .map(this::toDTO)
                    .toList();
        }

        // ADMIN can fetch only their own company
        if (userCompanyId == null || !userCompanyId.equals(companyId)) {
            throw new ConflictException("You can only access your company departments");
        }

        return departmentRepository.findAllByCompanyId(companyId)
                .stream()
                .map(this::toDTO)
                .toList();
    }
}