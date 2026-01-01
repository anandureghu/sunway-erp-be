package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeExperience;
import com.erp.dto.currentjob.EmployeeExperienceRequestDTO;
import com.erp.dto.currentjob.EmployeeExperienceResponseDTO;
import com.erp.mapper.EmployeeExperienceMapper;
import com.erp.repo.EmployeeExperienceRepo;
import com.erp.repo.EmployeeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeExperienceService {

    private final EmployeeExperienceRepo experienceRepo;
    private final EmployeeRepository employeeRepo;

    // ---------------- GET ALL ----------------
    public List<EmployeeExperienceResponseDTO> getAll(Long employeeId) {
        return experienceRepo.findByEmployeeId(employeeId)
                .stream()
                .map(EmployeeExperienceMapper::toDTO)
                .toList();
    }

    // ---------------- CREATE ----------------
    public EmployeeExperienceResponseDTO create(
            Long employeeId,
            EmployeeExperienceRequestDTO dto
    ) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeExperience experience = new EmployeeExperience();
        experience.setEmployee(employee);

        EmployeeExperienceMapper.updateEntity(experience, dto);

        experienceRepo.save(experience);
        return EmployeeExperienceMapper.toDTO(experience);
    }

    // ---------------- UPDATE ----------------
    public EmployeeExperienceResponseDTO update(
            Long employeeId,
            Long experienceId,
            EmployeeExperienceRequestDTO dto
    ) {
        EmployeeExperience experience = experienceRepo
                .findByIdAndEmployeeId(experienceId, employeeId)
                .orElseThrow(() -> new RuntimeException("Experience not found"));

        EmployeeExperienceMapper.updateEntity(experience, dto);
        return EmployeeExperienceMapper.toDTO(experience);
    }

    // ---------------- DELETE ----------------
    public void delete(Long employeeId, Long experienceId) {
        EmployeeExperience experience = experienceRepo
                .findByIdAndEmployeeId(experienceId, employeeId)
                .orElseThrow(() -> new RuntimeException("Experience not found"));

        experienceRepo.delete(experience);
    }
}
