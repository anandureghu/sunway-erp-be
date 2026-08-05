package com.erp.service.appraisal;

import com.erp.domain.appraisal.*;
import com.erp.domain.hr.Company;
import com.erp.dto.appraisal.*;
import com.erp.repo.appraisal.AppraisalConfigRepository;
import com.erp.repo.EmployeeAppraisalGoalRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.security.context.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Appraisal configuration lifecycle, scoped per company.
 *
 * <p>Configs carry an optional {@code company}. A {@code null} company marks a
 * legacy/global config that acts as a shared fallback. Reads prefer the caller's
 * own company config and fall back to the global one. Writes always target the
 * caller's company: editing the shared global config transparently forks a
 * company-owned copy (copy-on-write). SUPER_ADMIN bypasses the tenant guard.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AppraisalConfigService {

    private final AppraisalConfigRepository configRepository;
    private final EmployeeAppraisalGoalRepository employeeAppraisalGoalRepository;
    private final AuthContext authContext;
    private final CompanyRepository companyRepository;

    /* ================= CREATE ================= */

    public AppraisalConfigResponseDTO create(AppraisalConfigRequestDTO dto) {
        Long companyId = authContext.getCurrentCompanyId();
        // Multiple cycles per year are allowed; they are distinguished by name.
        boolean exists = companyId != null
                ? configRepository.findByCompany_IdAndYearAndCycleName(
                        companyId, dto.getYear(), dto.getCycleName()).isPresent()
                : configRepository.findByCompanyIsNullAndYearAndCycleName(
                        dto.getYear(), dto.getCycleName()).isPresent();
        if (exists) {
            throw new IllegalStateException(
                    "A cycle named '" + dto.getCycleName() + "' already exists for " + dto.getYear());
        }
        AppraisalConfig config = mapToEntity(dto);
        config.setCompany(resolveCompany(companyId));
        config.setStatus("DRAFT");
        validateRoleWeights(config);
        return mapToDTO(configRepository.save(config));
    }

    /* ================= UPDATE ================= */

    public AppraisalConfigResponseDTO update(Long id, AppraisalConfigRequestDTO dto) {
        AppraisalConfig existing = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found"));

        // Resolve the config the caller may actually write to (own, or a fork
        // of the shared global one). Throws if it belongs to another company.
        AppraisalConfig target = resolveWritableTarget(existing);

        if ("ACTIVE".equals(target.getStatus())) {
            throw new IllegalStateException("Cannot edit ACTIVE config");
        }

        target.setCycleName(dto.getCycleName());
        target.setStartMonth(dto.getStartMonth());
        target.setEndMonth(dto.getEndMonth());
        target.setMinGoals(dto.getMinGoals());
        target.setMaxGoals(dto.getMaxGoals());
        target.setEnableSelfAssessment(dto.getEnableSelfAssessment());
        target.setEnableMidYear(dto.getEnableMidYear());
        target.setEnablePIP(dto.getEnablePIP());

        // Preserve job-code configs whose KPI templates are already referenced by
        // existing employee appraisals — they must not be deleted/recreated.
        Set<String> lockedJobCodes = target.getRoleConfigs().stream()
                .filter(role -> role.getGoalTemplates().stream()
                        .anyMatch(template ->
                                employeeAppraisalGoalRepository.existsByTemplateId(template.getId())))
                .map(AppraisalRoleConfig::getJobCode)
                .collect(Collectors.toSet());

        target.getRoleConfigs().removeIf(role -> !lockedJobCodes.contains(role.getJobCode()));
        configRepository.saveAndFlush(target);

        if (dto.getJobConfigs() != null) {
            for (AppraisalRoleConfigRequestDTO jobDTO : dto.getJobConfigs()) {
                String jobCode = jobDTO.getJobCode();
                if (lockedJobCodes.contains(jobCode)) continue;

                AppraisalRoleConfig role = new AppraisalRoleConfig();
                role.setJobCode(jobCode);
                role.setConfig(target);

                if (jobDTO.getGoals() != null) {
                    for (AppraisalGoalTemplateRequestDTO goalDTO : jobDTO.getGoals()) {
                        AppraisalGoalTemplate goal = new AppraisalGoalTemplate();
                        goal.setKpi(goalDTO.getKpi());
                        goal.setDescription(goalDTO.getDescription());
                        goal.setWeight(goalDTO.getWeight());
                        goal.setActive(goalDTO.getActive() != null ? goalDTO.getActive() : true);
                        goal.setRoleConfig(role);
                        role.getGoalTemplates().add(goal);
                    }
                }
                target.getRoleConfigs().add(role);
            }
        }

        validateRoleWeights(target);
        return mapToDTO(configRepository.saveAndFlush(target));
    }

    /* ================= ACTIVATE ================= */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AppraisalConfigResponseDTO activate(Long id) {
        AppraisalConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found with id: " + id));
        assertWritable(config);

        // Multiple cycles may be ACTIVE at once for the same year, so activating
        // one does NOT close the others.
        config.setStatus("ACTIVE");
        return mapToDTO(configRepository.save(config));
    }

    /* ================= DEACTIVATE ================= */

    public AppraisalConfigResponseDTO deactivate(Long id) {
        AppraisalConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found"));
        assertWritable(config);
        if (!"ACTIVE".equals(config.getStatus())) {
            throw new IllegalStateException("Only ACTIVE config can be deactivated");
        }
        config.setStatus("DRAFT");
        return mapToDTO(configRepository.save(config));
    }

    /* ================= CLOSE ================= */

    public AppraisalConfigResponseDTO close(Long id) {
        AppraisalConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found"));
        assertWritable(config);
        if (!"ACTIVE".equals(config.getStatus())) {
            throw new IllegalStateException("Only ACTIVE config can be closed");
        }
        config.setStatus("CLOSED");
        return mapToDTO(configRepository.save(config));
    }

    /* ================= SAVE AND ACTIVATE ================= */

    public AppraisalConfigResponseDTO saveAndActivate(AppraisalConfigRequestDTO dto) {
        Long companyId = authContext.getCurrentCompanyId();
        // Match an existing cycle by (year, name); otherwise create a new one.
        Optional<AppraisalConfig> existing = companyId != null
                ? configRepository.findByCompany_IdAndYearAndCycleName(companyId, dto.getYear(), dto.getCycleName())
                : configRepository.findByCompanyIsNullAndYearAndCycleName(dto.getYear(), dto.getCycleName());
        Long configId = existing.isPresent()
                ? updateAndReturnId(existing.get().getId(), dto)
                : create(dto).getId();

        return activate(configId);
    }

    private Long updateAndReturnId(Long id, AppraisalConfigRequestDTO dto) {
        return update(id, dto).getId();
    }

    /* ================= DUPLICATE ================= */

    public AppraisalConfigResponseDTO duplicate(Integer fromYear, Integer toYear) {
        Long companyId = authContext.getCurrentCompanyId();

        AppraisalConfig source = (companyId != null
                ? configRepository.findFirstByCompany_IdAndYearOrderByIdDesc(companyId, fromYear)
                : Optional.<AppraisalConfig>empty())
                .or(() -> configRepository.findFirstByCompanyIsNullAndYearOrderByIdDesc(fromYear))
                .orElseThrow(() -> new IllegalArgumentException("No config found for year " + fromYear));

        String copyName = source.getCycleName() + " (copy)";
        boolean targetExists = companyId != null
                ? configRepository.findByCompany_IdAndYearAndCycleName(companyId, toYear, copyName).isPresent()
                : configRepository.findByCompanyIsNullAndYearAndCycleName(toYear, copyName).isPresent();
        if (targetExists) {
            throw new IllegalStateException("A cycle named '" + copyName + "' already exists for " + toYear);
        }

        AppraisalConfig copy = new AppraisalConfig();
        copy.setCompany(resolveCompany(companyId));
        copy.setYear(toYear);
        copy.setCycleName(copyName);
        copy.setStartMonth(source.getStartMonth());
        copy.setEndMonth(source.getEndMonth());
        copy.setMinGoals(source.getMinGoals());
        copy.setMaxGoals(source.getMaxGoals());
        copy.setEnableSelfAssessment(source.getEnableSelfAssessment());
        copy.setEnableMidYear(source.getEnableMidYear());
        copy.setEnablePIP(source.getEnablePIP());
        copy.setStatus("DRAFT");

        for (AppraisalRoleConfig sourceRole : source.getRoleConfigs()) {
            AppraisalRoleConfig role = new AppraisalRoleConfig();
            role.setJobCode(sourceRole.getJobCode());
            role.setConfig(copy);
            for (AppraisalGoalTemplate sourceGoal : sourceRole.getGoalTemplates()) {
                AppraisalGoalTemplate goal = new AppraisalGoalTemplate();
                goal.setKpi(sourceGoal.getKpi());
                goal.setDescription(sourceGoal.getDescription());
                goal.setWeight(sourceGoal.getWeight());
                goal.setActive(sourceGoal.getActive());
                goal.setRoleConfig(role);
                role.getGoalTemplates().add(goal);
            }
            copy.getRoleConfigs().add(role);
        }

        return mapToDTO(configRepository.save(copy));
    }

    /* ================= GET ACTIVE ================= */

    @Transactional(readOnly = true)
    public Optional<AppraisalConfigResponseDTO> getActive() {
        Long companyId = authContext.getCurrentCompanyId();
        Optional<AppraisalConfig> own = companyId != null
                ? configRepository.findFirstByStatusAndCompany_IdOrderByYearDesc("ACTIVE", companyId)
                : Optional.empty();
        return own
                .or(() -> configRepository.findFirstByStatusAndCompanyIsNullOrderByYearDesc("ACTIVE"))
                .map(this::mapToDTO);
    }

    /* ================= GET BY YEAR ================= */

    @Transactional(readOnly = true)
    public Optional<AppraisalConfigResponseDTO> getByYear(Integer year) {
        Long companyId = authContext.getCurrentCompanyId();
        Optional<AppraisalConfig> own = companyId != null
                ? configRepository.findFirstByCompany_IdAndYearOrderByIdDesc(companyId, year)
                : Optional.empty();
        return own
                .or(() -> configRepository.findFirstByCompanyIsNullAndYearOrderByIdDesc(year))
                .map(this::mapToDTO);
    }

    /* ================= LIST CYCLES ================= */

    /** All cycles for a year the caller can use: own company cycles plus shared global ones. */
    @Transactional(readOnly = true)
    public List<AppraisalConfigResponseDTO> listByYear(Integer year) {
        Long companyId = authContext.getCurrentCompanyId();
        List<AppraisalConfig> result = new java.util.ArrayList<>();
        if (companyId != null) {
            result.addAll(configRepository.findByCompany_IdAndYearOrderByIdDesc(companyId, year));
        }
        result.addAll(configRepository.findByCompanyIsNullAndYearOrderByIdDesc(year));
        return result.stream().map(this::mapToDTO).toList();
    }

    /** All ACTIVE cycles the caller can assign against: own active cycles plus shared global active ones. */
    @Transactional(readOnly = true)
    public List<AppraisalConfigResponseDTO> listActive() {
        Long companyId = authContext.getCurrentCompanyId();
        List<AppraisalConfig> result = new java.util.ArrayList<>();
        if (companyId != null) {
            result.addAll(configRepository.findByStatusAndCompany_Id("ACTIVE", companyId));
        }
        result.addAll(configRepository.findByStatusAndCompanyIsNull("ACTIVE"));
        return result.stream().map(this::mapToDTO).toList();
    }

    /* ================= DELETE ================= */

    public void delete(Long id) {
        AppraisalConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found"));
        assertWritable(config);

        if ("ACTIVE".equals(config.getStatus())) {
            throw new IllegalStateException("Cannot delete ACTIVE config. Close it first.");
        }

        boolean templatesInUse = config.getRoleConfigs().stream()
                .flatMap(role -> role.getGoalTemplates().stream())
                .anyMatch(template ->
                        employeeAppraisalGoalRepository.existsByTemplateId(template.getId()));

        if (templatesInUse) {
            throw new IllegalStateException(
                    "Cannot delete configuration because its KPI templates are already used in employee appraisals.");
        }

        configRepository.delete(config);
    }

    /* ================= TENANT GUARDS ================= */

    /** Config the caller may write to: own config, or a fork of the shared global one. */
    private AppraisalConfig resolveWritableTarget(AppraisalConfig existing) {
        if (isSuperAdmin() || sameCompany(existing)) {
            return existing;
        }
        if (existing.getCompany() == null) {
            // Copy-on-write: editing the shared global config creates/uses the
            // caller company's own copy (matched by year + cycle name).
            Long companyId = requireCompanyId();
            return configRepository
                    .findByCompany_IdAndYearAndCycleName(companyId, existing.getYear(), existing.getCycleName())
                    .orElseGet(() -> {
                        AppraisalConfig fork = new AppraisalConfig();
                        fork.setCompany(resolveCompany(companyId));
                        fork.setYear(existing.getYear());
                        fork.setCycleName(existing.getCycleName());
                        fork.setStatus("DRAFT");
                        return fork;
                    });
        }
        throw new AccessDeniedException("Configuration belongs to another company");
    }

    /** Block status changes / deletes on configs the caller does not own. */
    private void assertWritable(AppraisalConfig config) {
        if (isSuperAdmin()) return;
        Long companyId = config.getCompany() != null ? config.getCompany().getId() : null;
        if (companyId == null) {
            throw new AccessDeniedException(
                    "This is a shared global configuration. Save your company's own configuration first.");
        }
        Long current = authContext.getCurrentCompanyId();
        if (current == null || !current.equals(companyId)) {
            throw new AccessDeniedException("Configuration belongs to another company");
        }
    }

    private boolean sameCompany(AppraisalConfig config) {
        Long current = authContext.getCurrentCompanyId();
        Long owner = config.getCompany() != null ? config.getCompany().getId() : null;
        return current != null && owner != null && current.equals(owner);
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

    private Company resolveCompany(Long companyId) {
        return companyId == null ? null : companyRepository.findById(companyId).orElse(null);
    }

    /* ================= ENTITY MAPPING ================= */

    private AppraisalConfig mapToEntity(AppraisalConfigRequestDTO dto) {
        AppraisalConfig config = new AppraisalConfig();
        config.setYear(dto.getYear());
        config.setCycleName(dto.getCycleName());
        config.setStartMonth(dto.getStartMonth());
        config.setEndMonth(dto.getEndMonth());
        config.setMinGoals(dto.getMinGoals());
        config.setMaxGoals(dto.getMaxGoals());
        config.setEnableSelfAssessment(dto.getEnableSelfAssessment());
        config.setEnableMidYear(dto.getEnableMidYear());
        config.setEnablePIP(dto.getEnablePIP());

        if (dto.getJobConfigs() != null) {
            for (AppraisalRoleConfigRequestDTO jobDTO : dto.getJobConfigs()) {
                AppraisalRoleConfig role = new AppraisalRoleConfig();
                role.setJobCode(jobDTO.getJobCode());
                role.setConfig(config);
                if (jobDTO.getGoals() != null) {
                    for (AppraisalGoalTemplateRequestDTO goalDTO : jobDTO.getGoals()) {
                        AppraisalGoalTemplate goal = new AppraisalGoalTemplate();
                        goal.setKpi(goalDTO.getKpi());
                        goal.setDescription(goalDTO.getDescription());
                        goal.setWeight(goalDTO.getWeight());
                        goal.setActive(goalDTO.getActive() != null ? goalDTO.getActive() : true);
                        goal.setRoleConfig(role);
                        role.getGoalTemplates().add(goal);
                    }
                }
                config.getRoleConfigs().add(role);
            }
        }
        return config;
    }

    /* ================= RESPONSE MAPPING ================= */

    private AppraisalConfigResponseDTO mapToDTO(AppraisalConfig config) {
        List<RoleConfigResponseDTO> roleDTOs = config.getRoleConfigs().stream().map(role -> {
            List<EmployeeGoalDTO> goalDTOs = role.getGoalTemplates().stream().map(goal -> {
                EmployeeGoalDTO goalDTO = new EmployeeGoalDTO();
                goalDTO.setGoalId(goal.getId());
                goalDTO.setKpi(goal.getKpi());
                goalDTO.setDescription(goal.getDescription());
                goalDTO.setWeight(goal.getWeight());
                return goalDTO;
            }).toList();
            return RoleConfigResponseDTO.builder()
                    .jobCode(role.getJobCode())
                    .goals(goalDTOs)
                    .build();
        }).toList();

        return AppraisalConfigResponseDTO.builder()
                .id(config.getId())
                .year(config.getYear())
                .cycleName(config.getCycleName())
                .startMonth(config.getStartMonth())
                .endMonth(config.getEndMonth())
                .minGoals(config.getMinGoals())
                .maxGoals(config.getMaxGoals())
                .status(config.getStatus())
                .enableSelfAssessment(config.getEnableSelfAssessment())
                .enableMidYear(config.getEnableMidYear())
                .enablePIP(config.getEnablePIP())
                .jobConfigs(roleDTOs)
                .build();
    }

    /* ================= VALIDATION ================= */

    private void validateRoleWeights(AppraisalConfig config) {
        for (AppraisalRoleConfig role : config.getRoleConfigs()) {
            int totalWeight = role.getGoalTemplates().stream()
                    .filter(t -> Boolean.TRUE.equals(t.getActive()))
                    .mapToInt(AppraisalGoalTemplate::getWeight)
                    .sum();
            if (totalWeight != 100) {
                throw new IllegalStateException(
                        "Total weight for job code '" + role.getJobCode() +
                                "' must equal 100. Current: " + totalWeight);
            }
        }
    }
}
