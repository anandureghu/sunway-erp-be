package com.erp.service.hrsettings;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeCurrentJob;
import com.erp.domain.hr.Company;
import com.erp.domain.hrsettings.JobCode;
import com.erp.dto.hrsettings.JobCodeRequestDTO;
import com.erp.dto.hrsettings.JobCodeResponseDTO;
import com.erp.exception.NotFoundException;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.hrsettings.JobCodeRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.CurrentJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class JobCodeService {

    private final JobCodeRepository repository;
    private final CompanyRepository companyRepository;
    private final EmployeeCurrentJobRepo employeeCurrentJobRepo;
    private final AuthContext authContext;

    // CREATE
    public JobCodeResponseDTO create(JobCodeRequestDTO dto) {

        Company company = requireCurrentCompany();

        String code = normalizeCode(dto.getCode());

        repository.findByCompany_IdAndCode(company.getId(), code)
                .ifPresent(j -> {
                    throw new IllegalStateException(
                            "Job code \"" + code + "\" already exists for this company");
                });

        validateSalaryRange(dto.getMinSalary(), dto.getMaxSalary());

        JobCode jobCode = JobCode.builder()
                .code(code)
                .title(dto.getTitle())
                .level(dto.getLevel())
                .salaryGrade(dto.getSalaryGrade())
                .minSalary(dto.getMinSalary())
                .maxSalary(dto.getMaxSalary())
                .active(dto.getActive())
                .company(company)
                .build();

        return mapToDTO(repository.save(jobCode));
    }

    // GET ALL
    @Transactional(readOnly = true)
    public List<JobCodeResponseDTO> getAll() {
        Long companyId = requireCurrentCompany().getId();
        return repository.findByCompany_Id(companyId)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    // GET ACTIVE (Dropdown)
    @Transactional(readOnly = true)
    public List<JobCodeResponseDTO> getActive() {
        Long companyId = requireCurrentCompany().getId();
        return repository.findByCompany_IdAndActiveTrue(companyId)
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * Active job codes for the Current Job picker, each annotated with assignability.
     * Every active code is returned so the user can always see their codes; a code
     * already held by another still-employed person is flagged `assignable=false`
     * with `assignedTo` naming the holder (so the UI can show it disabled). The
     * employee's own current code stays assignable so editing works.
     */
    @Transactional(readOnly = true)
    public List<JobCodeResponseDTO> getAssignable(Long employeeId) {
        Long companyId = requireCurrentCompany().getId();
        long excludeId = employeeId != null ? employeeId : -1L;

        // jobCodeId -> holder name, for codes reserved by another still-employed person.
        Map<Long, String> holders = new HashMap<>();
        for (EmployeeCurrentJob cj : employeeCurrentJobRepo.findActiveHoldersExcluding(
                excludeId, CurrentJobService.JOB_CODE_FREED_STATUSES)) {
            if (cj.getJobCode() == null) continue;
            holders.putIfAbsent(cj.getJobCode().getId(), holderName(cj.getEmployee()));
        }

        return repository.findByCompany_IdAndActiveTrue(companyId).stream()
                .map(j -> {
                    JobCodeResponseDTO dto = mapToDTO(j);
                    String holder = holders.get(j.getId());
                    dto.setAssignable(holder == null);
                    dto.setAssignedTo(holder);
                    return dto;
                })
                .toList();
    }

    private String holderName(Employee e) {
        if (e == null) return "another employee";
        String name = ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                + (e.getLastName() == null ? "" : e.getLastName())).trim();
        if (!name.isEmpty()) return name;
        return e.getEmployeeNo() != null ? e.getEmployeeNo() : ("employee #" + e.getId());
    }

    // UPDATE
    public JobCodeResponseDTO update(Long id, JobCodeRequestDTO dto) {

        Long companyId = requireCurrentCompany().getId();

        JobCode existing = repository.findByIdAndCompany_Id(id, companyId)
                .orElseThrow(() -> new NotFoundException("Job code not found"));

        String code = normalizeCode(dto.getCode());

        if (!existing.getCode().equalsIgnoreCase(code)) {
            repository.findByCompany_IdAndCode(companyId, code)
                    .filter(j -> !j.getId().equals(id))
                    .ifPresent(j -> {
                        throw new IllegalStateException(
                                "Job code \"" + code + "\" already exists for this company");
                    });
        }

        validateSalaryRange(dto.getMinSalary(), dto.getMaxSalary());

        existing.setCode(code);
        existing.setTitle(dto.getTitle());
        existing.setLevel(dto.getLevel());
        existing.setSalaryGrade(dto.getSalaryGrade());
        existing.setMinSalary(dto.getMinSalary());
        existing.setMaxSalary(dto.getMaxSalary());
        existing.setActive(dto.getActive());

        return mapToDTO(repository.save(existing));
    }

    // DELETE
    public void delete(Long id) {

        Long companyId = requireCurrentCompany().getId();

        JobCode existing = repository.findByIdAndCompany_Id(id, companyId)
                .orElseThrow(() -> new NotFoundException("Job code not found"));

        if (employeeCurrentJobRepo.existsByJobCode_Id(id)) {
            throw new IllegalStateException(
                    "Cannot delete a job code that is still assigned to employees");
        }

        repository.delete(existing);
    }

    private Company requireCurrentCompany() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("No active company context");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new AccessDeniedException("Current company not found"));
    }

    /** Trim surrounding whitespace so " ENG1" and "ENG1" can't both exist. */
    private String normalizeCode(String code) {
        String trimmed = code == null ? "" : code.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Job code is required");
        }
        return trimmed;
    }

    private void validateSalaryRange(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Min salary cannot exceed max salary");
        }
    }

    private JobCodeResponseDTO mapToDTO(JobCode jobCode) {
        return JobCodeResponseDTO.builder()
                .id(jobCode.getId())
                .code(jobCode.getCode())
                .title(jobCode.getTitle())
                .level(jobCode.getLevel())
                .salaryGrade(jobCode.getSalaryGrade())
                .minSalary(jobCode.getMinSalary())
                .maxSalary(jobCode.getMaxSalary())
                .active(jobCode.getActive())
                .companyId(jobCode.getCompany() != null ? jobCode.getCompany().getId() : null)
                .build();
    }
}
