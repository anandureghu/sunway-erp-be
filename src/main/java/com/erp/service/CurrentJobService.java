package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeCurrentJob;
import com.erp.domain.hr.Department;
import com.erp.domain.hrsettings.JobCode;
import com.erp.dto.currentjob.EmployeeCurrentJobRequestDTO;
import com.erp.dto.currentjob.EmployeeCurrentJobResponseDTO;
import com.erp.exception.NotFoundException;
import com.erp.mapper.EmployeeCurrentJobMapper;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.DepartmentRepository;
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

    // ======================================================
    // GET CURRENT JOB
    // ======================================================
    @Transactional(readOnly = true)
    public EmployeeCurrentJobResponseDTO get(Long employeeId) {

        EmployeeCurrentJob job = currentJobRepo
                .findByEmployee_Id(employeeId)
                .orElse(null);

        if (job == null) return null;

        // ✅ Force-initialize lazy relations while session is open
        initializeLazyRelations(job);

        return EmployeeCurrentJobMapper.toDTO(job);
    }

    // ======================================================
    // CREATE CURRENT JOB
    // ======================================================
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

        EmployeeCurrentJob job = new EmployeeCurrentJob();
        job.setEmployee(employee);
        job.setJobCode(jobCode);
        job.setDepartment(department);

        EmployeeCurrentJobMapper.updateEntity(job, dto);

        // ✅ Save and flush so ID is generated before toDTO is called
        EmployeeCurrentJob saved = currentJobRepo.saveAndFlush(job);

        return EmployeeCurrentJobMapper.toDTO(saved);
    }

    // ======================================================
    // UPDATE CURRENT JOB
    // ======================================================
    public EmployeeCurrentJobResponseDTO update(Long employeeId,
                                                EmployeeCurrentJobRequestDTO dto) {

        EmployeeCurrentJob job = currentJobRepo.findByEmployee_Id(employeeId)
                .orElseThrow(() -> new NotFoundException("Current job not found"));

        JobCode jobCode = jobCodeRepo.findById(dto.getJobCodeId())
                .orElseThrow(() -> new NotFoundException("Job code not found"));

        assertSameCompany(job.getEmployee(), jobCode);

        Department department = departmentRepo.findById(dto.getDepartmentId())
                .orElseThrow(() -> new NotFoundException("Department not found"));

        job.setJobCode(jobCode);
        job.setDepartment(department);

        EmployeeCurrentJobMapper.updateEntity(job, dto);

        // ✅ Flush to persist before mapping
        EmployeeCurrentJob saved = currentJobRepo.saveAndFlush(job);

        return EmployeeCurrentJobMapper.toDTO(saved);
    }

    private void assertSameCompany(Employee employee, JobCode jobCode) {
        Long empCompanyId = employee.getCompany() != null ? employee.getCompany().getId() : null;
        Long jobCompanyId = jobCode.getCompany() != null ? jobCode.getCompany().getId() : null;
        if (empCompanyId == null || jobCompanyId == null || !empCompanyId.equals(jobCompanyId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Job code does not belong to this employee's company");
        }
    }

    // ======================================================
    // HELPER — force initialize lazy relations inside session
    // ======================================================
    private void initializeLazyRelations(EmployeeCurrentJob job) {
        // Touch each lazy relation to trigger Hibernate to load them
        // This must be called while the @Transactional session is still open
        if (job.getEmployee() != null) {
            job.getEmployee().getId(); // ✅ triggers load
        }
        if (job.getJobCode() != null) {
            job.getJobCode().getId();  // ✅ triggers load
        }
        if (job.getDepartment() != null) {
            job.getDepartment().getId(); // ✅ triggers load
        }
    }
}