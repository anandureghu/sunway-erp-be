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

    public AllowanceTypeResponseDTO create(AllowanceTypeRequestDTO dto) {

        AllowanceType type = AllowanceType.builder()
                .name(dto.getName().toUpperCase())
                .description(dto.getDescription())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        return mapToResponse(repository.save(type));
    }

    public List<AllowanceTypeResponseDTO> getActiveTypes() {

        return repository.findByActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public AllowanceTypeResponseDTO update(Long id, AllowanceTypeRequestDTO dto) {

        AllowanceType type = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Allowance type not found"));

        type.setName(dto.getName().toUpperCase());
        type.setDescription(dto.getDescription());
        type.setActive(dto.getActive());

        return mapToResponse(type);
    }

    public void deactivate(Long id) {

        AllowanceType type = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Allowance type not found"));

        type.setActive(false);
    }

    private AllowanceTypeResponseDTO mapToResponse(AllowanceType type) {

        return AllowanceTypeResponseDTO.builder()
                .id(type.getId())
                .name(type.getName())
                .description(type.getDescription())
                .active(type.isActive())
                .build();
    }
}