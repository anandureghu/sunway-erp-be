package com.erp.service.hr;

import com.erp.domain.hr.Company;
import com.erp.domain.hr.Department;
import com.erp.dto.hr.CreateDepartmentDTO;
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
    private final AuthContext authContext;

    public DepartmentService(DepartmentRepository departmentRepository,
                             CompanyRepository companyRepository,
                             AuthContext authContext) {
        this.departmentRepository = departmentRepository;
        this.companyRepository = companyRepository;
        this.authContext = authContext;
    }

    public List<Department> getDepartmentsForCurrentUser() {
        Long userId = authContext.getCurrentUserId();
        return departmentRepository.findAllByCompanyCreatedBy(String.valueOf(userId));
    }

    public Department getDepartmentById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
    }

    public Department createDepartment(CreateDepartmentDTO dto) {
        Company company = companyRepository.findById(dto.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // ✅ Optional ownership check
        Long userId = authContext.getCurrentUserId();
        if (!company.getCreatedBy().equals(String.valueOf(userId))) {
            throw new RuntimeException("You do not have permission to add departments to this company");
        }

        Department department = Department.builder()
                .departmentCode(dto.getDepartmentCode())
                .departmentName(dto.getDepartmentName())
                .managerId(dto.getManagerId())
                .company(company)
                .createdAt(Instant.now())
                .build();

        return departmentRepository.save(department);
    }

    public void deleteDepartment(Long id) {
        departmentRepository.deleteById(id);
    }

    public List<Department> getDepartmentsByCompanyId(Long companyId) {
        return departmentRepository.findAllByCompanyId(companyId);
    }
}
