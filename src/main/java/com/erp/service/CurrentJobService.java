package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeCurrentJob;
import com.erp.domain.EmployeeStatus;
import com.erp.domain.hr.Department;
import com.erp.domain.hr.Division;
import com.erp.domain.hrsettings.JobCode;
import com.erp.domain.security.AppModule;
import com.erp.dto.currentjob.EmployeeCurrentJobRequestDTO;
import com.erp.dto.currentjob.EmployeeCurrentJobResponseDTO;
import com.erp.exception.NotFoundException;
import com.erp.mapper.EmployeeCurrentJobMapper;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.hr.DepartmentRepository;
import com.erp.repo.hr.DivisionRepository;
import com.erp.repo.hrsettings.JobCodeRepository;
import com.erp.security.guard.EmployeeAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class CurrentJobService {

    /**
     * Statuses for which an employee no longer occupies their job code, freeing it
     * to be assigned to someone else. Everyone still on the roster (ACTIVE, ON_LEAVE,
     * INACTIVE) keeps their job code reserved.
     */
    public static final Set<EmployeeStatus> JOB_CODE_FREED_STATUSES = EnumSet.of(
            EmployeeStatus.TERMINATED, EmployeeStatus.RESIGNED, EmployeeStatus.RETIRED);

    private final EmployeeCurrentJobRepo currentJobRepo;
    private final EmployeeRepository employeeRepo;
    private final JobCodeRepository jobCodeRepo;
    private final DepartmentRepository departmentRepo;
    private final DivisionRepository divisionRepo;
    private final EmployeeAccessGuard employeeAccessGuard;

    @Transactional(readOnly = true)
    public EmployeeCurrentJobResponseDTO get(Long employeeId) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        employeeAccessGuard.assertCanRead(employee, AppModule.CURRENT_JOB);

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

        employeeAccessGuard.assertCanWrite(employee, AppModule.CURRENT_JOB);

        JobCode jobCode = jobCodeRepo.findById(dto.getJobCodeId())
                .orElseThrow(() -> new NotFoundException("Job code not found"));

        assertSameCompany(employee, jobCode);
        assertJobCodeAvailable(jobCode, employeeId);

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

        employeeAccessGuard.assertCanWrite(job.getEmployee(), AppModule.CURRENT_JOB);

        JobCode jobCode = jobCodeRepo.findById(dto.getJobCodeId())
                .orElseThrow(() -> new NotFoundException("Job code not found"));

        assertSameCompany(job.getEmployee(), jobCode);
        assertJobCodeAvailable(jobCode, job.getEmployee().getId());

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

    /**
     * A job code may be held by only one still-employed person at a time. If another
     * non-exited employee already has it as their current job, reject the assignment
     * (it frees up once that holder is terminated / resigned / retired).
     */
    private void assertJobCodeAvailable(JobCode jobCode, Long employeeId) {
        boolean taken = currentJobRepo.isJobCodeHeldByAnotherActiveEmployee(
                jobCode.getId(), employeeId, JOB_CODE_FREED_STATUSES);
        if (!taken) {
            return;
        }
        String holder = currentJobRepo
                .findFirstByJobCode_IdAndEmployee_IdNot(jobCode.getId(), employeeId)
                .map(cj -> {
                    Employee e = cj.getEmployee();
                    String name = ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                            + (e.getLastName() == null ? "" : e.getLastName())).trim();
                    return name.isEmpty() ? ("employee #" + e.getId()) : name;
                })
                .orElse("another active employee");
        throw new IllegalStateException(
                "Job code " + jobCode.getCode() + " is already assigned to " + holder
                        + ". A job code can be held by only one active employee — it frees up "
                        + "once that employee is terminated.");
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
