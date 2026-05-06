package com.erp.service.common;

import com.erp.domain.hr.AllowanceType;
import com.erp.dto.hr.AllowanceTypeRequestDTO;
import com.erp.dto.hr.AllowanceTypeResponseDTO;
import com.erp.repo.hr.AllowanceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AllowanceTypeService {

    private final AllowanceTypeRepository repository;

    // ================= CREATE =================

    public AllowanceTypeResponseDTO create(AllowanceTypeRequestDTO dto) {
        String normalizedName = normalizeName(dto.getName());

        repository.findByNameIgnoreCase(normalizedName)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Allowance type already exists"
                    );
                });

        AllowanceType type = AllowanceType.builder()
                .name(normalizedName)
                .description(normalizeDescription(dto.getDescription()))
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

    // ================= GET ALL =================

    @Transactional(readOnly = true)
    public List<AllowanceTypeResponseDTO> getAll() {
        return repository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // ================= GET BY ID =================

    @Transactional(readOnly = true)
    public AllowanceTypeResponseDTO getById(Long id) {
        AllowanceType type = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Allowance type not found"
                ));

        return mapToResponse(type);
    }

    // ================= UPDATE =================

    public AllowanceTypeResponseDTO update(Long id, AllowanceTypeRequestDTO dto) {
        AllowanceType type = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Allowance type not found"
                ));

        String normalizedName = normalizeName(dto.getName());

        repository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Allowance type already exists"
                    );
                });

        type.setName(normalizedName);
        type.setDescription(normalizeDescription(dto.getDescription()));
        type.setActive(dto.getActive() != null ? dto.getActive() : type.isActive());

        return mapToResponse(repository.save(type));
    }

    // ================= DEACTIVATE =================

    public void deactivate(Long id) {
        AllowanceType type = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Allowance type not found"
                ));

        if (!type.isActive()) {
            return;
        }

        type.setActive(false);
        repository.save(type);
    }

    // ================= ACTIVATE =================

    public void activate(Long id) {
        AllowanceType type = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Allowance type not found"
                ));

        if (type.isActive()) {
            return;
        }

        type.setActive(true);
        repository.save(type);
    }

    // ================= HELPERS =================

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Allowance type name is required"
            );
        }
        return name.trim().toUpperCase();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
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