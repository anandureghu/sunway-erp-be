package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeCurrentJob;
import com.erp.domain.hr.Department;
import com.erp.domain.hr.Division;
import com.erp.domain.hrsettings.JobCode;
import com.erp.dto.currentjob.EmployeeCurrentJobRequestDTO;
import com.erp.dto.currentjob.EmployeeCurrentJobResponseDTO;
import com.erp.exception.NotFoundException;
import com.erp.mapper.EmployeeCurrentJobMapper;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.repo.hr.DivisionRepository;
import com.erp.repo.hrsettings.JobCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CurrentJobService {

    private final EmployeeCurrentJobRepo currentJobRepo;
    private final EmployeeRepository employeeRepo;
    private final JobCodeRepository jobCodeRepo;
    private final DepartmentRepository departmentRepo;
    private final DivisionRepository divisionRepo;

    @Transactional(readOnly = true)
    public EmployeeCurrentJobResponseDTO get(Long employeeId) {

        EmployeeCurrentJob job = currentJobRepo
                .findByEmployee_Id(employeeId)
                .orElse(null);

        if (job == null) return null;

        initializeLazyRelations(job);

        return EmployeeCurrentJobMapper.toDTO(job);
    }

    public EmployeeCurrentJobResponseDTO create(Long employeeId,
                                                EmployeeCurrentJobRequestDTO dto) {

        if (currentJobRepo.existsByEmployee_Id(employeeId)) {
            throw new RuntimeException("Current job already exists for this employee");
        }

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        JobCode jobCode = jobCodeRepo.findById(dto.getJobCodeId())
                .orElseThrow(() -> new NotFoundException("Job code not found"));

        assertSameCompany(employee, jobCode);

        Department department = departmentRepo.findById(dto.getDepartmentId())
                .orElseThrow(() -> new NotFoundException("Department not found"));
        Division division = resolveDivision(dto.getDivisionId(), department);

        EmployeeCurrentJob job = new EmployeeCurrentJob();
        job.setEmployee(employee);
        job.setJobCode(jobCode);
        job.setDepartment(department);
        job.setDivision(division);

        EmployeeCurrentJobMapper.updateEntity(job, dto);
        applyReportingManager(job, employee, dto.getReportingManagerId());

        EmployeeCurrentJob saved = currentJobRepo.saveAndFlush(job);

        return EmployeeCurrentJobMapper.toDTO(saved);
    }

    public EmployeeCurrentJobResponseDTO update(Long employeeId,
                                                EmployeeCurrentJobRequestDTO dto) {

        EmployeeCurrentJob job = currentJobRepo.findByEmployee_Id(employeeId)
                .orElseThrow(() -> new NotFoundException("Current job not found"));

        JobCode jobCode = jobCodeRepo.findById(dto.getJobCodeId())
                .orElseThrow(() -> new NotFoundException("Job code not found"));

        assertSameCompany(job.getEmployee(), jobCode);

        Department department = departmentRepo.findById(dto.getDepartmentId())
                .orElseThrow(() -> new NotFoundException("Department not found"));
        Division division = resolveDivision(dto.getDivisionId(), department);

        job.setJobCode(jobCode);
        job.setDepartment(department);
        job.setDivision(division);

        EmployeeCurrentJobMapper.updateEntity(job, dto);
        applyReportingManager(job, job.getEmployee(), dto.getReportingManagerId());

        EmployeeCurrentJob saved = currentJobRepo.saveAndFlush(job);

        return EmployeeCurrentJobMapper.toDTO(saved);
    }

    private void applyReportingManager(EmployeeCurrentJob job, Employee employee, Long managerId) {
        if (managerId == null) {
            job.setReportingManager(null);
            return;
        }

        if (employee.getId() != null && employee.getId().equals(managerId)) {
            throw new IllegalArgumentException("Reporting manager cannot be the employee themselves");
        }

        Employee manager = employeeRepo.findById(managerId)
                .orElseThrow(() -> new NotFoundException("Reporting manager not found"));

        Long empCompanyId = employee.getCompany() != null ? employee.getCompany().getId() : null;
        Long mgrCompanyId = manager.getCompany() != null ? manager.getCompany().getId() : null;
        if (empCompanyId == null || !empCompanyId.equals(mgrCompanyId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Reporting manager must belong to the same company");
        }

        job.setReportingManager(manager);
    }

    private Division resolveDivision(Long divisionId, Department department) {
        if (divisionId == null) {
            return null;
        }

        Division division = divisionRepo.findById(divisionId)
                .orElseThrow(() -> new NotFoundException("Division not found"));

        if (division.getDepartment() == null
                || !division.getDepartment().getId().equals(department.getId())) {
            throw new IllegalArgumentException("Division must belong to the selected department");
        }

        return division;
    }

    private void assertSameCompany(Employee employee, JobCode jobCode) {
        Long empCompanyId = employee.getCompany() != null ? employee.getCompany().getId() : null;
        Long jobCompanyId = jobCode.getCompany() != null ? jobCode.getCompany().getId() : null;
        if (empCompanyId == null || jobCompanyId == null || !empCompanyId.equals(jobCompanyId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Job code does not belong to this employee's company");
        }
    }

    private void initializeLazyRelations(EmployeeCurrentJob job) {
        if (job.getEmployee() != null) {
            job.getEmployee().getId();
        }
        if (job.getJobCode() != null) {
            job.getJobCode().getId();
        }
        if (job.getDepartment() != null) {
            job.getDepartment().getId();
        }
        if (job.getDivision() != null) {
            job.getDivision().getId();
        }
        if (job.getReportingManager() != null) {
            job.getReportingManager().getId();
        }
    }
}
