package com.erp.service.hrsettings;
import com.azure.core.exception.ResourceNotFoundException;
import com.erp.domain.hrsettings.JobCode;
import com.erp.dto.hrsettings.JobCodeRequestDTO;
import com.erp.dto.hrsettings.JobCodeResponseDTO;
import com.erp.exception.NotFoundException;
import com.erp.repo.hrsettings.JobCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class JobCodeService {

    private final JobCodeRepository repository;

    // CREATE
    public JobCodeResponseDTO create(JobCodeRequestDTO dto) {

        repository.findByCode(dto.getCode())
                .ifPresent(j -> {
                    throw new RuntimeException("Job code already exists");
                });

        JobCode jobCode = JobCode.builder()
                .code(dto.getCode())
                .title(dto.getTitle())
                .level(dto.getLevel())
                .grade(dto.getGrade())
                .active(dto.getActive())
                .build();

        return mapToDTO(repository.save(jobCode));
    }

    // GET ALL
    @Transactional(readOnly = true)
    public List<JobCodeResponseDTO> getAll() {
        return repository.findAll()
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    // GET ACTIVE (Dropdown)
    @Transactional(readOnly = true)
    public List<JobCodeResponseDTO> getActive() {
        return repository.findByActiveTrue()
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    // UPDATE
    public JobCodeResponseDTO update(Long id, JobCodeRequestDTO dto) {

        JobCode existing = repository.findById(id)
                .orElseThrow(() ->
                        new NotFoundException("Job code not found"));

        existing.setCode(dto.getCode());
        existing.setTitle(dto.getTitle());
        existing.setLevel(dto.getLevel());
        existing.setGrade(dto.getGrade());
        existing.setActive(dto.getActive());

        return mapToDTO(repository.save(existing));
    }

    // SOFT DELETE
    public void delete(Long id) {

        JobCode existing = repository.findById(id)
                .orElseThrow(() ->
                        new NotFoundException("Job code not found"));

        existing.setActive(false);
        repository.save(existing);
    }

    private JobCodeResponseDTO mapToDTO(JobCode jobCode) {
        return JobCodeResponseDTO.builder()
                .id(jobCode.getId())
                .code(jobCode.getCode())
                .title(jobCode.getTitle())
                .level(jobCode.getLevel())
                .grade(jobCode.getGrade())
                .active(jobCode.getActive())
                .build();
    }
}