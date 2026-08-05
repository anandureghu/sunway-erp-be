package com.erp.service.appraisal;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeCurrentJob;
import com.erp.domain.appraisal.*;
import com.erp.dto.appraisal.EmployeeAppraisalRequestDTO;
import com.erp.dto.appraisal.EmployeeAppraisalResponseDTO;
import com.erp.dto.appraisal.EmployeeGoalDTO;
import com.erp.repo.EmployeeAppraisalRepository;
import com.erp.repo.EmployeeCurrentJobRepo;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.appraisal.AppraisalConfigRepository;
import com.erp.repo.appraisal.AppraisalGoalTemplateRepository;
import com.erp.repo.appraisal.AppraisalRoleConfigRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeAppraisalService {

    private final EmployeeRepository              employeeRepository;
    private final EmployeeAppraisalRepository     appraisalRepository;
    private final AppraisalConfigRepository       configRepository;
    private final AppraisalRoleConfigRepository   roleConfigRepository;
    private final AppraisalGoalTemplateRepository templateRepository;
    private final EmployeeCurrentJobRepo          currentJobRepository;
    private final AuthContext                     authContext;

    private static final Set<String> VALID_MONTHS = Set.of(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER");

    /* =========================================================
       CREATE — one appraisal per employee per month per year
       ========================================================= */

    public EmployeeAppraisalResponseDTO createAppraisal(
            Long employeeId, Long configId, String month) {

        // Normalize month to uppercase (JANUARY, FEBRUARY ...)
        String normalizedMonth = month == null ? "" : month.toUpperCase().trim();
        if (!VALID_MONTHS.contains(normalizedMonth)) {
            throw new IllegalArgumentException(
                    "Invalid month: '" + month + "'. Expected a full month name (e.g. JANUARY).");
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        assertSameTenant(employee);

        // The cycle the admin chose to assign this employee to.
        AppraisalConfig config = configRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal cycle not found"));
        assertConfigUsableFor(config, employee);
        if (!"ACTIVE".equals(config.getStatus())) {
            throw new IllegalStateException(
                    "Appraisal cycle '" + config.getCycleName() + "' is not active.");
        }

        Integer year = config.getYear();

        // One appraisal per employee, per cycle, per month.
        if (appraisalRepository.existsByEmployeeIdAndConfig_IdAndMonth(
                employeeId, configId, normalizedMonth)) {
            throw new IllegalStateException(
                    "Appraisal already exists for " + normalizedMonth + " in cycle '"
                            + config.getCycleName() + "'.");
        }

        // Appraisal KPIs are configured per job code — resolve the employee's
        // current job code and match it against the cycle's job configs.
        EmployeeCurrentJob currentJob = currentJobRepository.findByEmployee_Id(employeeId)
                .orElseThrow(() -> new IllegalStateException(
                        "Employee has no current job assignment. Assign a job code before "
                                + "creating an appraisal."));
        String jobCode = currentJob.getJobCode() != null ? currentJob.getJobCode().getCode() : null;
        if (jobCode == null || jobCode.isBlank()) {
            throw new IllegalStateException(
                    "Employee's current job has no job code assigned.");
        }

        AppraisalRoleConfig roleConfig = roleConfigRepository
                .findByConfigIdAndJobCode(config.getId(), jobCode)
                .orElseThrow(() -> new IllegalStateException(
                        "Cycle '" + config.getCycleName() + "' has no KPIs configured for job code '"
                                + jobCode + "'. Add this job code to the cycle, or pick a cycle "
                                + "that covers it."));

        List<AppraisalGoalTemplate> templates =
                templateRepository.findByRoleConfigIdAndActiveTrue(roleConfig.getId());

        if (templates.isEmpty()) {
            throw new IllegalStateException(
                    "No active KPI templates found for job code: " + jobCode);
        }

        EmployeeAppraisal appraisal = new EmployeeAppraisal();
        appraisal.setEmployee(employee);
        appraisal.setConfig(config);
        appraisal.setYear(year);
        appraisal.setMonth(normalizedMonth);
        appraisal.setStatus("DRAFT");

        for (AppraisalGoalTemplate template : templates) {
            EmployeeAppraisalGoal goal = new EmployeeAppraisalGoal();
            goal.setAppraisal(appraisal);
            goal.setTemplate(template);
            goal.setKpi(template.getKpi());
            goal.setDescription(template.getDescription());
            goal.setWeight(template.getWeight());
            appraisal.getGoals().add(goal);
        }

        return mapToResponse(appraisalRepository.save(appraisal));
    }

    /* =========================================================
       SELF SUBMIT  (DRAFT → SELF_SUBMITTED)
       ========================================================= */

    public EmployeeAppraisalResponseDTO submitSelfAssessment(
            Long employeeId, Long appraisalId, EmployeeAppraisalRequestDTO dto) {

        EmployeeAppraisal appraisal = loadOwnedAppraisal(appraisalId, employeeId);

        if (!"DRAFT".equals(appraisal.getStatus())) {
            throw new IllegalStateException(
                    "Only DRAFT appraisals can be self-submitted. Current: " + appraisal.getStatus());
        }

        updateGoalRatings(appraisal, dto.getGoals());
        appraisal.setEmployeeComments(dto.getEmployeeComments());
        appraisal.setStatus("SELF_SUBMITTED");

        return mapToResponse(appraisal);
    }

    /* =========================================================
       MANAGER REVIEW  (SELF_SUBMITTED → MANAGER_REVIEWED)
       ========================================================= */

    public EmployeeAppraisalResponseDTO submitManagerReview(
            Long employeeId, Long appraisalId, EmployeeAppraisalRequestDTO dto) {

        EmployeeAppraisal appraisal = loadOwnedAppraisal(appraisalId, employeeId);

        if (!"SELF_SUBMITTED".equals(appraisal.getStatus())) {
            throw new IllegalStateException(
                    "Only SELF_SUBMITTED appraisals can be manager-reviewed. Current: " + appraisal.getStatus());
        }

        updateGoalRatings(appraisal, dto.getGoals());
        appraisal.setManagerComments(dto.getManagerComments());
        appraisal.setOverallScore(calculateWeightedScore(appraisal));
        appraisal.setStatus("MANAGER_REVIEWED");

        return mapToResponse(appraisal);
    }

    /* =========================================================
       LOCK  (MANAGER_REVIEWED → LOCKED)
       ========================================================= */

    public EmployeeAppraisalResponseDTO lockAppraisal(Long employeeId, Long appraisalId) {

        EmployeeAppraisal appraisal = loadOwnedAppraisal(appraisalId, employeeId);

        if (!"MANAGER_REVIEWED".equals(appraisal.getStatus())) {
            throw new IllegalStateException(
                    "Only MANAGER_REVIEWED appraisals can be locked. Current: " + appraisal.getStatus());
        }

        appraisal.setStatus("LOCKED");
        return mapToResponse(appraisal);
    }

    /* =========================================================
       UNLOCK  (LOCKED → MANAGER_REVIEWED)
       ========================================================= */

    public EmployeeAppraisalResponseDTO unlockAppraisal(Long employeeId, Long appraisalId) {

        EmployeeAppraisal appraisal = loadOwnedAppraisal(appraisalId, employeeId);

        if (!"LOCKED".equals(appraisal.getStatus())) {
            throw new IllegalStateException(
                    "Only LOCKED appraisals can be unlocked. Current: " + appraisal.getStatus());
        }

        appraisal.setStatus("MANAGER_REVIEWED");
        return mapToResponse(appraisal);
    }

    /* =========================================================
       DELETE  (blocked if LOCKED)
       ========================================================= */

    public void delete(Long employeeId, Long appraisalId) {

        EmployeeAppraisal appraisal = loadOwnedAppraisal(appraisalId, employeeId);

        if ("LOCKED".equals(appraisal.getStatus())) {
            throw new IllegalStateException(
                    "Locked appraisals cannot be deleted. Unlock it first.");
        }

        appraisalRepository.delete(appraisal);
    }

    /* =========================================================
       FORCE DELETE — any status
       ========================================================= */

    public void forceDelete(Long employeeId, Long appraisalId) {

        EmployeeAppraisal appraisal = loadOwnedAppraisal(appraisalId, employeeId);

        appraisalRepository.delete(appraisal);
    }

    /* =========================================================
       QUERIES
       ========================================================= */

    @Transactional(readOnly = true)
    public Page<EmployeeAppraisalResponseDTO> getAppraisalsByEmployee(
            Long employeeId, Pageable pageable) {
        if (isSuperAdmin()) {
            return appraisalRepository.findByEmployeeId(employeeId, pageable)
                    .map(this::mapToResponse);
        }
        return appraisalRepository
                .findByEmployeeIdAndEmployee_Company_Id(
                        employeeId, requireCompanyId(), pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeAppraisalResponseDTO> getAppraisalsByYear(
            Integer year, Pageable pageable) {
        if (isSuperAdmin()) {
            return appraisalRepository.findByYear(year, pageable)
                    .map(this::mapToResponse);
        }
        return appraisalRepository
                .findByYearAndEmployee_Company_Id(year, requireCompanyId(), pageable)
                .map(this::mapToResponse);
    }

    /** A cycle is usable for an employee if it is the shared global one or belongs to their company. */
    private void assertConfigUsableFor(AppraisalConfig config, Employee employee) {
        Long configCompanyId = config.getCompany() != null ? config.getCompany().getId() : null;
        if (configCompanyId == null) return; // shared/global cycle
        Long employeeCompanyId = employee.getCompany() != null ? employee.getCompany().getId() : null;
        if (employeeCompanyId == null || !configCompanyId.equals(employeeCompanyId)) {
            throw new AccessDeniedException("This appraisal cycle belongs to a different company");
        }
    }

    /* =========================================================
       TENANT GUARDS
       ========================================================= */

    /** Load an appraisal scoped to (id, employee) and verify it belongs to the caller's company. */
    private EmployeeAppraisal loadOwnedAppraisal(Long appraisalId, Long employeeId) {
        EmployeeAppraisal appraisal = appraisalRepository
                .findByIdAndEmployeeId(appraisalId, employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal not found"));
        assertSameTenant(appraisal.getEmployee());
        return appraisal;
    }

    /** Reject access when the target employee belongs to another company (SUPER_ADMIN may cross tenants). */
    private void assertSameTenant(Employee employee) {
        if (isSuperAdmin()) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        Long employeeCompanyId = employee.getCompany() != null ? employee.getCompany().getId() : null;
        if (currentCompanyId == null || employeeCompanyId == null
                || !currentCompanyId.equals(employeeCompanyId)) {
            throw new AccessDeniedException("Appraisal belongs to a different company");
        }
    }

    private boolean isSuperAdmin() {
        return "SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole());
    }

    private Long requireCompanyId() {
        Long companyId = authContext.getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("No company context for the current user");
        }
        return companyId;
    }

    /* =========================================================
       HELPERS
       ========================================================= */

    private void updateGoalRatings(
            EmployeeAppraisal appraisal, List<EmployeeGoalDTO> goalDTOs) {

        if (goalDTOs == null || goalDTOs.isEmpty()) return;

        for (EmployeeGoalDTO dto : goalDTOs) {
            EmployeeAppraisalGoal goal = appraisal.getGoals().stream()
                    .filter(g -> g.getId().equals(dto.getGoalId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Goal not found with id: " + dto.getGoalId()));

            if (dto.getSelfRating()     != null) goal.setSelfRating(dto.getSelfRating());
            if (dto.getManagerRating()  != null) goal.setManagerRating(dto.getManagerRating());
            if (dto.getSelfComment()    != null) goal.setSelfComment(dto.getSelfComment());
            if (dto.getManagerComment() != null) goal.setManagerComment(dto.getManagerComment());
        }
    }

    /** Weighted manager score, or {@code null} when no goal has been manager-rated yet. */
    private Double calculateWeightedScore(EmployeeAppraisal appraisal) {
        double total = 0; int weightSum = 0;
        for (EmployeeAppraisalGoal goal : appraisal.getGoals()) {
            if (goal.getManagerRating() != null && goal.getWeight() != null) {
                total     += (double) goal.getManagerRating() * goal.getWeight();
                weightSum += goal.getWeight();
            }
        }
        return weightSum == 0 ? null : total / weightSum;
    }

    /* =========================================================
       RESPONSE MAPPING
       ========================================================= */

    private EmployeeAppraisalResponseDTO mapToResponse(EmployeeAppraisal a) {

        List<EmployeeGoalDTO> goals = a.getGoals().stream().map(g -> {
            EmployeeGoalDTO dto = new EmployeeGoalDTO();
            dto.setGoalId(g.getId());
            dto.setKpi(g.getKpi());
            dto.setDescription(g.getDescription());
            dto.setWeight(g.getWeight());
            dto.setSelfRating(g.getSelfRating());
            dto.setManagerRating(g.getManagerRating());
            dto.setSelfComment(g.getSelfComment());
            dto.setManagerComment(g.getManagerComment());
            return dto;
        }).toList();

        return EmployeeAppraisalResponseDTO.builder()
                .id(a.getId())
                .configId(a.getConfig() != null ? a.getConfig().getId() : null)
                .cycleName(a.getConfig() != null ? a.getConfig().getCycleName() : null)
                .employeeId(a.getEmployee().getId())
                .employeeName(a.getEmployee().getFirstName() + " " + a.getEmployee().getLastName())
                .employeeRole(
                        a.getEmployee().getUser() != null && a.getEmployee().getUser().getCompanyRole() != null
                                ? a.getEmployee().getUser().getCompanyRole()
                                : a.getEmployee().getRole()
                )
                .year(a.getYear())
                .month(a.getMonth())
                .status(a.getStatus())
                .overallScore(a.getOverallScore())
                .goals(goals)
                .employeeComments(a.getEmployeeComments())
                .managerComments(a.getManagerComments())
                .createdDate(a.getCreatedAt())
                .updatedDate(a.getUpdatedDate())
                .build();
    }
}