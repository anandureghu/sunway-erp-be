package com.erp.service.appraisal;

import com.erp.domain.Employee;
import com.erp.domain.appraisal.*;
import com.erp.dto.appraisal.EmployeeAppraisalRequestDTO;
import com.erp.dto.appraisal.EmployeeAppraisalResponseDTO;
import com.erp.dto.appraisal.EmployeeGoalDTO;
import com.erp.repo.EmployeeAppraisalRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.appraisal.AppraisalConfigRepository;
import com.erp.repo.appraisal.AppraisalGoalTemplateRepository;
import com.erp.repo.appraisal.AppraisalRoleConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeAppraisalService {

    private final EmployeeRepository              employeeRepository;
    private final EmployeeAppraisalRepository     appraisalRepository;
    private final AppraisalConfigRepository       configRepository;
    private final AppraisalRoleConfigRepository   roleConfigRepository;
    private final AppraisalGoalTemplateRepository templateRepository;

    /* =========================================================
       CREATE — one appraisal per employee per month per year
       ========================================================= */

    public EmployeeAppraisalResponseDTO createAppraisal(
            Long employeeId, Integer year, String month) {

        // Normalize month to uppercase (JANUARY, FEBRUARY ...)
        String normalizedMonth = month.toUpperCase().trim();

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        // Check uniqueness: one appraisal per employee per month per year
        if (appraisalRepository.existsByEmployeeIdAndYearAndMonth(
                employeeId, year, normalizedMonth)) {
            throw new IllegalStateException(
                    "Appraisal already exists for " + normalizedMonth + " " + year);
        }

        // ✅ Use List to avoid "Query did not return a unique result" when duplicates exist
        List<AppraisalConfig> configs = configRepository.findByYearAndStatus(year, "ACTIVE");
        if (configs.isEmpty()) {
            throw new IllegalStateException("No ACTIVE appraisal config for year " + year);
        }
        AppraisalConfig config = configs.get(0);

        // ✅ Use companyRole ("Admin", "Manager") — matches what HR configured in appraisal settings
        //    Falls back to security enum name only if companyRole is not set
        String employeeRoleName = (employee.getUser() != null && employee.getUser().getCompanyRole() != null
                && !employee.getUser().getCompanyRole().isBlank())
                ? employee.getUser().getCompanyRole()
                : employee.getRole();

        AppraisalRoleConfig roleConfig = roleConfigRepository
                .findByConfigIdAndRoleName(config.getId(), employeeRoleName)
                .orElseThrow(() -> new IllegalStateException(
                        "No role config found for role: " + employeeRoleName +
                                ". Please configure this role in HR Settings → Appraisal."));

        List<AppraisalGoalTemplate> templates =
                templateRepository.findByRoleConfigIdAndActiveTrue(roleConfig.getId());

        if (templates.isEmpty()) {
            throw new IllegalStateException(
                    "No active KPI templates found for role: " + employee.getRole());
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

        EmployeeAppraisal appraisal = appraisalRepository
                .findByIdAndEmployeeId(appraisalId, employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal not found"));

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

        EmployeeAppraisal appraisal = appraisalRepository
                .findByIdAndEmployeeId(appraisalId, employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal not found"));

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

        EmployeeAppraisal appraisal = appraisalRepository
                .findByIdAndEmployeeId(appraisalId, employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal not found"));

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

        EmployeeAppraisal appraisal = appraisalRepository
                .findByIdAndEmployeeId(appraisalId, employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal not found"));

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

        EmployeeAppraisal appraisal = appraisalRepository
                .findByIdAndEmployeeId(appraisalId, employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal not found"));

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

        EmployeeAppraisal appraisal = appraisalRepository
                .findByIdAndEmployeeId(appraisalId, employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Appraisal not found"));

        appraisalRepository.delete(appraisal);
    }

    /* =========================================================
       QUERIES
       ========================================================= */

    @Transactional(readOnly = true)
    public Page<EmployeeAppraisalResponseDTO> getAppraisalsByEmployee(
            Long employeeId, Pageable pageable) {
        return appraisalRepository
                .findByEmployeeId(employeeId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeAppraisalResponseDTO> getAppraisalsByYear(
            Integer year, Pageable pageable) {
        return appraisalRepository
                .findByYear(year, pageable)
                .map(this::mapToResponse);
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

    private double calculateWeightedScore(EmployeeAppraisal appraisal) {
        double total = 0; int weightSum = 0;
        for (EmployeeAppraisalGoal goal : appraisal.getGoals()) {
            if (goal.getManagerRating() != null && goal.getWeight() != null) {
                total     += (double) goal.getManagerRating() * goal.getWeight();
                weightSum += goal.getWeight();
            }
        }
        return weightSum == 0 ? 0 : total / weightSum;
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
                .createdDate(a.getCreatedDate())
                .updatedDate(a.getUpdatedDate())
                .build();
    }
}