package com.erp.service.common;

import com.erp.domain.hr.AllowanceType;
import com.erp.dto.hr.AllowanceTypeRequestDTO;
import com.erp.dto.hr.AllowanceTypeResponseDTO;
import com.erp.repo.hr.AllowanceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AllowanceTypeService {

    private final AllowanceTypeRepository repository;

    // ================= CREATE =================

    public AllowanceTypeResponseDTO create(AllowanceTypeRequestDTO dto) {

        String normalizedName = dto.getName().trim().toUpperCase();

        repository.findByNameIgnoreCase(normalizedName)
                .ifPresent(existing -> {
                    throw new RuntimeException("Allowance type already exists");
                });

        AllowanceType type = AllowanceType.builder()
                .name(normalizedName)
                .description(dto.getDescription())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        return mapToResponse(repository.save(type));
    }

    // ================= GET ACTIVE =================

    @Transactional(readOnly = true)
    public List<AllowanceTypeResponseDTO> getActiveTypes() {

        return repository.findByActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ================= UPDATE =================

    public AllowanceTypeResponseDTO update(Long id, AllowanceTypeRequestDTO dto) {

        AllowanceType type = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Allowance type not found"));

        String normalizedName = dto.getName().trim().toUpperCase();

        // Prevent renaming to an existing name
        repository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new RuntimeException("Allowance type already exists");
                });

        type.setName(normalizedName);
        type.setDescription(dto.getDescription());
        type.setActive(dto.getActive() != null ? dto.getActive() : true);

        return mapToResponse(type);
    }

    // ================= DEACTIVATE =================

    public void deactivate(Long id) {

        AllowanceType type = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Allowance type not found"));

        type.setActive(false);
    }

    // ================= MAPPER =================

    private AllowanceTypeResponseDTO mapToResponse(AllowanceType type) {

        return AllowanceTypeResponseDTO.builder()
                .id(type.getId())
                .name(type.getName())
                .description(type.getDescription())
                .active(type.isActive())
                .build();
    }
}