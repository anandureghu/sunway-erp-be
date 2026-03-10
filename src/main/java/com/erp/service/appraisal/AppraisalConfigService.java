package com.erp.service.appraisal;

import com.erp.domain.appraisal.*;
import com.erp.dto.appraisal.*;
import com.erp.repo.appraisal.AppraisalConfigRepository;
import com.erp.repo.EmployeeAppraisalGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AppraisalConfigService {

    private final AppraisalConfigRepository configRepository;
    private final EmployeeAppraisalGoalRepository employeeAppraisalGoalRepository;

    /* ================= CREATE ================= */

    public AppraisalConfigResponseDTO create(AppraisalConfigRequestDTO dto) {

        if (configRepository.findByYear(dto.getYear()).isPresent()) {
            throw new IllegalStateException(
                    "Config already exists for year " + dto.getYear()
            );
        }

        AppraisalConfig config = mapToEntity(dto);
        config.setStatus("DRAFT");

        validateRoleWeights(config);

        return mapToDTO(configRepository.save(config));
    }

    /* ================= UPDATE ================= */

    public AppraisalConfigResponseDTO update(Long id, AppraisalConfigRequestDTO dto) {

        AppraisalConfig existing = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found"));

        if ("ACTIVE".equals(existing.getStatus())) {
            throw new IllegalStateException("Cannot edit ACTIVE config");
        }

        // Always safe to update metadata
        existing.setCycleName(dto.getCycleName());
        existing.setEnableSelfAssessment(dto.getEnableSelfAssessment());
        existing.setEnableMidYear(dto.getEnableMidYear());
        existing.setEnablePIP(dto.getEnablePIP());

        // Find which existing role names have templates already used in employee appraisals
        Set<String> lockedRoleNames = existing.getRoleConfigs().stream()
                .filter(role -> role.getGoalTemplates().stream()
                        .anyMatch(template ->
                                employeeAppraisalGoalRepository.existsByTemplateId(template.getId())
                        )
                )
                .map(AppraisalRoleConfig::getRoleName)
                .collect(Collectors.toSet());

        // Find role names in the incoming DTO
        Set<String> incomingRoleNames = dto.getRoles() == null ? Set.of() :
                dto.getRoles().stream()
                        .map(AppraisalRoleConfigRequestDTO::getRoleName)
                        .collect(Collectors.toSet());

        // Remove only roles that are NOT locked (safe to delete)
        existing.getRoleConfigs().removeIf(role -> !lockedRoleNames.contains(role.getRoleName()));
        configRepository.saveAndFlush(existing);

        // Add/replace roles from the DTO
        if (dto.getRoles() != null) {
            for (AppraisalRoleConfigRequestDTO roleDTO : dto.getRoles()) {

                String roleName = roleDTO.getRoleName();

                if (lockedRoleNames.contains(roleName)) {
                    // This role's templates are in use — skip replacing its KPIs
                    continue;
                }

                // New or unlocked role — add it fresh
                AppraisalRoleConfig role = new AppraisalRoleConfig();
                role.setRoleName(roleName);
                role.setConfig(existing);

                if (roleDTO.getGoals() != null) {
                    for (AppraisalGoalTemplateRequestDTO goalDTO : roleDTO.getGoals()) {
                        AppraisalGoalTemplate goal = new AppraisalGoalTemplate();
                        goal.setKpi(goalDTO.getKpi());
                        goal.setDescription(goalDTO.getDescription());
                        goal.setWeight(goalDTO.getWeight());
                        goal.setActive(goalDTO.getActive() != null ? goalDTO.getActive() : true);
                        goal.setRoleConfig(role);
                        role.getGoalTemplates().add(goal);
                    }
                }

                existing.getRoleConfigs().add(role);
            }
        }

        validateRoleWeights(existing);

        return mapToDTO(configRepository.saveAndFlush(existing));
    }

    /* ================= ACTIVATE ================= */

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AppraisalConfigResponseDTO activate(Long id) {

        AppraisalConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Config not found with id: " + id));

        configRepository.findByYearAndStatus(config.getYear(), "ACTIVE")
                .ifPresent(active -> {
                    active.setStatus("CLOSED");
                    configRepository.save(active);
                });

        config.setStatus("ACTIVE");

        return mapToDTO(configRepository.save(config));
    }

    /* ================= CLOSE ================= */

    public AppraisalConfigResponseDTO close(Long id) {

        AppraisalConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found"));

        if (!"ACTIVE".equals(config.getStatus())) {
            throw new IllegalStateException("Only ACTIVE config can be closed");
        }

        config.setStatus("CLOSED");

        return mapToDTO(configRepository.save(config));
    }

    /* ================= SAVE AND ACTIVATE ================= */

    public AppraisalConfigResponseDTO saveAndActivate(AppraisalConfigRequestDTO dto) {

        Optional<AppraisalConfig> existing = configRepository.findByYear(dto.getYear());

        Long configId;

        if (existing.isPresent()) {
            AppraisalConfig config = existing.get();

            if ("ACTIVE".equals(config.getStatus())) {
                throw new IllegalStateException(
                        "An ACTIVE config already exists for year " + dto.getYear() +
                                ". Close or deactivate it before activating a new one."
                );
            }

            configId = config.getId();
            update(configId, dto);

        } else {
            AppraisalConfigResponseDTO created = create(dto);
            configId = created.getId();
        }

        return activate(configId);
    }

    /* ================= GET ACTIVE ================= */

    @Transactional(readOnly = true)
    public Optional<AppraisalConfigResponseDTO> getActive() {
        return configRepository.findByStatus("ACTIVE").map(this::mapToDTO);
    }

    /* ================= GET BY YEAR ================= */

    @Transactional(readOnly = true)
    public Optional<AppraisalConfigResponseDTO> getByYear(Integer year) {
        return configRepository.findByYear(year).map(this::mapToDTO);
    }

    /* ================= DELETE ================= */

    public void delete(Long id) {

        AppraisalConfig config = configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Config not found"));

        if ("ACTIVE".equals(config.getStatus())) {
            throw new IllegalStateException("Cannot delete ACTIVE config. Close it first.");
        }

        boolean templatesInUse = config.getRoleConfigs().stream()
                .flatMap(role -> role.getGoalTemplates().stream())
                .anyMatch(template ->
                        employeeAppraisalGoalRepository.existsByTemplateId(template.getId())
                );

        if (templatesInUse) {
            throw new IllegalStateException(
                    "Cannot delete configuration because its KPI templates are already used in employee appraisals."
            );
        }

        configRepository.delete(config);
    }

    /* ================= ENTITY MAPPING ================= */

    private AppraisalConfig mapToEntity(AppraisalConfigRequestDTO dto) {

        AppraisalConfig config = new AppraisalConfig();
        config.setYear(dto.getYear());
        config.setCycleName(dto.getCycleName());
        config.setEnableSelfAssessment(dto.getEnableSelfAssessment());
        config.setEnableMidYear(dto.getEnableMidYear());
        config.setEnablePIP(dto.getEnablePIP());

        if (dto.getRoles() != null) {
            for (AppraisalRoleConfigRequestDTO roleDTO : dto.getRoles()) {

                AppraisalRoleConfig role = new AppraisalRoleConfig();
                role.setRoleName(roleDTO.getRoleName());
                role.setConfig(config);

                if (roleDTO.getGoals() != null) {
                    for (AppraisalGoalTemplateRequestDTO goalDTO : roleDTO.getGoals()) {

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

        List<RoleConfigResponseDTO> roleDTOs =
                config.getRoleConfigs().stream().map(role -> {

                    List<EmployeeGoalDTO> goalDTOs =
                            role.getGoalTemplates().stream().map(goal -> {

                                EmployeeGoalDTO goalDTO = new EmployeeGoalDTO();
                                goalDTO.setGoalId(goal.getId());
                                goalDTO.setKpi(goal.getKpi());
                                goalDTO.setDescription(goal.getDescription());
                                goalDTO.setWeight(goal.getWeight());
                                return goalDTO;

                            }).toList();

                    return RoleConfigResponseDTO.builder()
                            .roleName(role.getRoleName())
                            .goals(goalDTOs)
                            .build();

                }).toList();

        return AppraisalConfigResponseDTO.builder()
                .id(config.getId())
                .year(config.getYear())
                .cycleName(config.getCycleName())
                .status(config.getStatus())
                .enableSelfAssessment(config.getEnableSelfAssessment())
                .enableMidYear(config.getEnableMidYear())
                .enablePIP(config.getEnablePIP())
                .roles(roleDTOs)
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
                        "Total weight for role '" + role.getRoleName() +
                                "' must equal 100. Current: " + totalWeight
                );
            }
        }
    }
}