package com.erp.service.hrsettings;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeCurrentJob;
import com.erp.domain.enums.JobCodeHoldReason;
import com.erp.domain.enums.JobCodeStatus;
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
    private final com.erp.repo.hr.DepartmentRepository departmentRepository;
    private final com.erp.repo.hr.DivisionRepository divisionRepository;
    private final AuthContext authContext;
    private final JobCodeOccupancyService occupancyService;

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

        BigDecimal minSalary = applyCompanyMinBasicFloor(dto.getMinSalary(), company);
        BigDecimal maxSalary = dto.getMaxSalary();
        if (maxSalary != null && minSalary != null && maxSalary.compareTo(minSalary) < 0) {
            maxSalary = minSalary;
        }
        validateSalaryRange(minSalary, maxSalary);

        JobCode jobCode = JobCode.builder()
                .code(code)
                .title(dto.getTitle())
                .level(dto.getLevel())
                .salaryGrade(dto.getSalaryGrade())
                .minSalary(minSalary)
                .maxSalary(maxSalary)
                .active(dto.getActive())
                .company(company)
                .build();

        applyDefaults(jobCode, dto, company.getId());
        // A new code is VACANT unless HR puts it straight on hold.
        jobCode.setStatus(JobCodeStatus.VACANT);
        applyRequestedStatus(jobCode, dto, null);

        return mapToDTO(repository.save(jobCode));
    }

    /** Resolve and set the current-job defaults (department, division, classification,
        work location) from the request. */
    private void applyDefaults(JobCode jobCode, JobCodeRequestDTO dto, Long companyId) {
        if (dto.getDepartmentId() != null) {
            jobCode.setDepartment(departmentRepository.findById(dto.getDepartmentId())
                    .filter(d -> d.getCompany() != null && companyId.equals(d.getCompany().getId()))
                    .orElseThrow(() -> new NotFoundException("Department not found")));
        } else {
            jobCode.setDepartment(null);
        }
        if (dto.getDivisionId() != null) {
            jobCode.setDivision(divisionRepository.findById(dto.getDivisionId())
                    .orElseThrow(() -> new NotFoundException("Division not found")));
        } else {
            jobCode.setDivision(null);
        }
        jobCode.setEmploymentCategory(parseEnum(
                com.erp.domain.enums.EmploymentCategory.class, dto.getEmploymentCategory()));
        jobCode.setEmploymentType(parseEnum(
                com.erp.domain.enums.EmploymentType.class, dto.getEmploymentType()));
        jobCode.setWorkLocation(blankToNull(dto.getWorkLocation()));
        jobCode.setWorkCity(blankToNull(dto.getWorkCity()));
        jobCode.setWorkCountry(blankToNull(dto.getWorkCountry()));
    }

    /**
     * HR may set a code VACANT or ON_HOLD (with a comment). OCCUPIED is never set by
     * hand — it follows assignments — and an occupied position cannot be put on hold.
     * A null status leaves the current state unchanged.
     */
    private void applyRequestedStatus(JobCode jobCode, JobCodeRequestDTO dto, String holder) {
        String raw = blankToNull(dto.getStatus());
        if (raw == null) {
            return;
        }
        JobCodeStatus requested;
        try {
            requested = JobCodeStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status must be Vacant or On Hold");
        }
        if (requested == JobCodeStatus.OCCUPIED) {
            return; // automatic — ignore
        }
        if (holder != null) {
            if (requested == JobCodeStatus.ON_HOLD) {
                throw new IllegalStateException("Job code " + jobCode.getCode()
                        + " is occupied by " + holder + " and cannot be put on hold. "
                        + "It becomes vacant when the employee leaves or is made inactive.");
            }
            return; // stays OCCUPIED
        }
        // A hold awaiting approval is closed only by Approve / Reject — not by editing.
        boolean awaitingApproval = jobCode.getStatus() == JobCodeStatus.ON_HOLD
                && jobCode.getHoldReason() == JobCodeHoldReason.PENDING_APPROVAL;
        if (awaitingApproval && requested != JobCodeStatus.ON_HOLD) {
            throw new IllegalStateException("Job code " + jobCode.getCode()
                    + " is on hold pending approval — approve or reject the hold to close it.");
        }
        if (requested == JobCodeStatus.ON_HOLD) {
            JobCodeHoldReason reason = parseEnum(JobCodeHoldReason.class, dto.getHoldReason());
            if (reason == null) {
                throw new IllegalArgumentException(
                        "Choose why the position is on hold: pending approval or reserved");
            }
            if (awaitingApproval && reason != JobCodeHoldReason.PENDING_APPROVAL) {
                throw new IllegalStateException("Job code " + jobCode.getCode()
                        + " is pending approval — approve or reject the hold to close it.");
            }
            String comment = blankToNull(dto.getHoldComment());
            if (comment == null) {
                throw new IllegalArgumentException(
                        "Add a comment explaining why the position is on hold");
            }
            if (comment.length() > 500) {
                throw new IllegalArgumentException("Hold comment must be 500 characters or fewer");
            }
            if (jobCode.getStatus() != JobCodeStatus.ON_HOLD || jobCode.getHoldSince() == null) {
                jobCode.setHoldSince(java.time.LocalDate.now());
            }
            jobCode.setStatus(JobCodeStatus.ON_HOLD);
            jobCode.setHoldComment(comment);
            jobCode.setHoldReason(reason);
        } else {
            if (jobCode.getStatus() == JobCodeStatus.ON_HOLD) {
                // Editing a reserved hold back to Vacant is a release.
                recordDecision(jobCode, "RELEASED", null);
            }
            clearHold(jobCode);
        }
    }

    // ── Closing a hold ──

    /** Position Approvals queue: pending-approval holds plus the last 20 decisions. */
    @Transactional(readOnly = true)
    public com.erp.dto.hrsettings.JobCodeHoldQueueDTO holdQueue() {
        Long companyId = requireCurrentCompany().getId();
        List<JobCode> all = repository.findByCompany_Id(companyId);
        List<JobCodeResponseDTO> pending = all.stream()
                .filter(j -> j.getStatus() == JobCodeStatus.ON_HOLD
                        && (j.getHoldReason() == null
                                || j.getHoldReason() == JobCodeHoldReason.PENDING_APPROVAL))
                .sorted(java.util.Comparator.comparing(JobCode::getHoldSince,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(this::mapToDTO)
                .toList();
        List<JobCodeResponseDTO> recent = all.stream()
                .filter(j -> j.getHoldDecidedAt() != null
                        && ("APPROVED".equals(j.getHoldDecision())
                                || "REJECTED".equals(j.getHoldDecision())))
                .sorted(java.util.Comparator.comparing(JobCode::getHoldDecidedAt).reversed())
                .limit(20)
                .map(this::mapToDTO)
                .toList();
        return com.erp.dto.hrsettings.JobCodeHoldQueueDTO.builder()
                .pending(pending)
                .recent(recent)
                .build();
    }

    /**
     * Approve a hold that is pending approval: the position is open again (VACANT)
     * and can be assigned.
     */
    public JobCodeResponseDTO approveHold(Long id, String note) {
        JobCode jobCode = requireOnHold(id, JobCodeHoldReason.PENDING_APPROVAL);
        recordDecision(jobCode, "APPROVED", note);
        clearHold(jobCode);
        jobCode.setActive(true);
        return mapToDTO(repository.save(jobCode));
    }

    /**
     * Reject a hold that is pending approval: the position is not approved, so the
     * code goes back to VACANT and is deactivated (hidden from assignment). A reason
     * is required.
     */
    public JobCodeResponseDTO rejectHold(Long id, String note) {
        String reason = blankToNull(note);
        if (reason == null) {
            throw new IllegalArgumentException("Add a reason for rejecting the position");
        }
        JobCode jobCode = requireOnHold(id, JobCodeHoldReason.PENDING_APPROVAL);
        recordDecision(jobCode, "REJECTED", reason);
        clearHold(jobCode);
        jobCode.setActive(false);
        return mapToDTO(repository.save(jobCode));
    }

    /** Release a reserved position: back to VACANT so anyone can be assigned. */
    public JobCodeResponseDTO releaseHold(Long id, String note) {
        JobCode jobCode = requireOnHold(id, JobCodeHoldReason.RESERVED);
        recordDecision(jobCode, "RELEASED", note);
        clearHold(jobCode);
        return mapToDTO(repository.save(jobCode));
    }

    private JobCode requireOnHold(Long id, JobCodeHoldReason expected) {
        Long companyId = requireCurrentCompany().getId();
        JobCode jobCode = repository.findByIdAndCompany_Id(id, companyId)
                .orElseThrow(() -> new NotFoundException("Job code not found"));
        if (jobCode.getStatus() != JobCodeStatus.ON_HOLD) {
            throw new IllegalStateException("Job code " + jobCode.getCode() + " is not on hold");
        }
        JobCodeHoldReason actual = jobCode.getHoldReason() != null
                ? jobCode.getHoldReason() : JobCodeHoldReason.PENDING_APPROVAL;
        if (actual != expected) {
            throw new IllegalStateException(expected == JobCodeHoldReason.PENDING_APPROVAL
                    ? "Job code " + jobCode.getCode() + " is reserved, not pending approval — release it instead."
                    : "Job code " + jobCode.getCode() + " is pending approval — approve or reject it instead.");
        }
        return jobCode;
    }

    private void recordDecision(JobCode jobCode, String decision, String note) {
        String clean = blankToNull(note);
        if (clean != null && clean.length() > 500) {
            throw new IllegalArgumentException("Note must be 500 characters or fewer");
        }
        jobCode.setHoldDecision(decision);
        jobCode.setHoldDecisionNote(clean);
        jobCode.setHoldDecidedBy(currentUserName());
        jobCode.setHoldDecidedAt(java.time.LocalDateTime.now());
    }

    private static void clearHold(JobCode jobCode) {
        jobCode.setStatus(JobCodeStatus.VACANT);
        jobCode.setHoldComment(null);
        jobCode.setHoldSince(null);
        jobCode.setHoldReason(null);
    }

    private String currentUserName() {
        try {
            var user = authContext.getCurrentUser();
            if (user != null) {
                if (user.getFullName() != null && !user.getFullName().isBlank()) return user.getFullName();
                if (user.getUsername() != null) return user.getUsername();
                return user.getEmail();
            }
        } catch (Exception ignored) {
            // no user context
        }
        return null;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // GET ALL (occupancy is re-synced first so the status is never stale)
    public List<JobCodeResponseDTO> getAll() {
        Long companyId = requireCurrentCompany().getId();
        Map<Long, String> holders = occupancyService.syncCompany(companyId);
        return repository.findByCompany_Id(companyId)
                .stream()
                .map(j -> withHolder(mapToDTO(j), holders))
                .toList();
    }

    // GET ACTIVE (Dropdown)
    public List<JobCodeResponseDTO> getActive() {
        Long companyId = requireCurrentCompany().getId();
        Map<Long, String> holders = occupancyService.syncCompany(companyId);
        return repository.findByCompany_IdAndActiveTrue(companyId)
                .stream()
                .map(j -> withHolder(mapToDTO(j), holders))
                .toList();
    }

    private JobCodeResponseDTO withHolder(JobCodeResponseDTO dto, Map<Long, String> holders) {
        dto.setOccupiedBy(holders.get(dto.getId()));
        return dto;
    }

    /**
     * Active job codes for the Current Job picker, each annotated with assignability.
     * Every active code is returned so the user can always see their codes; a code
     * already held by another still-employed person is flagged `assignable=false`
     * with `assignedTo` naming the holder (so the UI can show it disabled). The
     * employee's own current code stays assignable so editing works. Codes on hold
     * are flagged not assignable too (the UI shows them with the hold reason).
     */
    public List<JobCodeResponseDTO> getAssignable(Long employeeId) {
        Long companyId = requireCurrentCompany().getId();
        occupancyService.syncCompany(companyId);
        long excludeId = employeeId != null ? employeeId : -1L;

        // jobCodeId -> holder name, for codes reserved by another still-employed person.
        Map<Long, String> holders = new HashMap<>();
        for (EmployeeCurrentJob cj : employeeCurrentJobRepo.findActiveHoldersExcluding(
                excludeId, CurrentJobService.JOB_CODE_FREED_STATUSES)) {
            if (cj.getJobCode() == null) continue;
            holders.putIfAbsent(cj.getJobCode().getId(),
                    JobCodeOccupancyService.holderName(cj.getEmployee()));
        }

        return repository.findByCompany_IdAndActiveTrue(companyId).stream()
                .map(j -> {
                    JobCodeResponseDTO dto = mapToDTO(j);
                    String holder = holders.get(j.getId());
                    boolean onHold = j.getStatus() == JobCodeStatus.ON_HOLD;
                    dto.setAssignable(holder == null && !onHold);
                    dto.setAssignedTo(holder);
                    return dto;
                })
                .toList();
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

        Company company = existing.getCompany() != null
                ? existing.getCompany()
                : requireCurrentCompany();
        BigDecimal minSalary = applyCompanyMinBasicFloor(dto.getMinSalary(), company);
        BigDecimal maxSalary = dto.getMaxSalary();
        if (maxSalary != null && minSalary != null && maxSalary.compareTo(minSalary) < 0) {
            maxSalary = minSalary;
        }
        validateSalaryRange(minSalary, maxSalary);

        existing.setCode(code);
        existing.setTitle(dto.getTitle());
        existing.setLevel(dto.getLevel());
        existing.setSalaryGrade(dto.getSalaryGrade());
        existing.setMinSalary(minSalary);
        existing.setMaxSalary(maxSalary);
        existing.setActive(dto.getActive());
        applyDefaults(existing, dto, companyId);
        String holder = occupancyService.sync(existing);
        applyRequestedStatus(existing, dto, holder);

        JobCodeResponseDTO saved = mapToDTO(repository.save(existing));
        saved.setOccupiedBy(holder);
        return saved;
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

    /**
     * Null or below-floor min salary → company min basic salary.
     */
    private BigDecimal applyCompanyMinBasicFloor(BigDecimal minSalary, Company company) {
        BigDecimal floor = companyMinBasic(company);
        if (floor == null) {
            return minSalary;
        }
        if (minSalary == null || minSalary.compareTo(floor) < 0) {
            return floor;
        }
        return minSalary;
    }

    private BigDecimal companyMinBasic(Company company) {
        if (company == null || company.getMinimumMonthlyWage() == null) {
            return null;
        }
        return company.getMinimumMonthlyWage();
    }

    /**
     * Floor every job-code minSalary up to company min basic salary.
     * Never lowers higher mins; raises maxSalary when it would fall below the new min.
     */
    public int floorMinSalariesFromCompany(Long companyId) {
        if (companyId == null) {
            return 0;
        }
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null) {
            return 0;
        }
        BigDecimal floor = companyMinBasic(company);
        if (floor == null) {
            return 0;
        }
        int updated = 0;
        for (JobCode jobCode : repository.findByCompany_Id(companyId)) {
            boolean dirty = false;
            BigDecimal min = jobCode.getMinSalary();
            if (min == null || min.compareTo(floor) < 0) {
                jobCode.setMinSalary(floor);
                min = floor;
                dirty = true;
            }
            BigDecimal max = jobCode.getMaxSalary();
            if (max != null && max.compareTo(min) < 0) {
                jobCode.setMaxSalary(min);
                dirty = true;
            }
            if (dirty) {
                repository.save(jobCode);
                updated++;
            }
        }
        return updated;
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
                .status(jobCode.getStatus() != null ? jobCode.getStatus().name() : null)
                .holdComment(jobCode.getStatus() == JobCodeStatus.ON_HOLD ? jobCode.getHoldComment() : null)
                .holdSince(jobCode.getStatus() == JobCodeStatus.ON_HOLD ? jobCode.getHoldSince() : null)
                .holdReason(jobCode.getStatus() == JobCodeStatus.ON_HOLD
                        ? (jobCode.getHoldReason() != null ? jobCode.getHoldReason()
                                : JobCodeHoldReason.PENDING_APPROVAL).name()
                        : null)
                .holdDecision(jobCode.getHoldDecision())
                .holdDecisionNote(jobCode.getHoldDecisionNote())
                .holdDecidedBy(jobCode.getHoldDecidedBy())
                .holdDecidedAt(jobCode.getHoldDecidedAt())
                .companyId(jobCode.getCompany() != null ? jobCode.getCompany().getId() : null)
                .departmentId(jobCode.getDepartment() != null ? jobCode.getDepartment().getId() : null)
                .departmentName(jobCode.getDepartment() != null
                        ? jobCode.getDepartment().getDepartmentName() : null)
                .divisionId(jobCode.getDivision() != null ? jobCode.getDivision().getId() : null)
                .divisionName(jobCode.getDivision() != null ? jobCode.getDivision().getName() : null)
                .employmentCategory(jobCode.getEmploymentCategory() != null
                        ? jobCode.getEmploymentCategory().name() : null)
                .employmentType(jobCode.getEmploymentType() != null
                        ? jobCode.getEmploymentType().name() : null)
                .workLocation(jobCode.getWorkLocation())
                .workCity(jobCode.getWorkCity())
                .workCountry(jobCode.getWorkCountry())
                .build();
    }
}
