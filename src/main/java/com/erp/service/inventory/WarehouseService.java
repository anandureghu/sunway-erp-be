package com.erp.service.inventory;

import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Warehouse;
import com.erp.dto.inventory.WarehouseCreateDTO;
import com.erp.dto.inventory.WarehouseResponseDTO;
import com.erp.dto.inventory.WarehouseUpdateDTO;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class WarehouseService {

    private final WarehouseRepository repo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final AuthContext auth;

    public WarehouseService(
            WarehouseRepository repo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            AuthContext auth
    ) {
        this.repo = repo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.auth = auth;
    }

    // --------------------------
    // Create
    // --------------------------
    public WarehouseResponseDTO create(WarehouseCreateDTO dto) {

        Long companyId = auth.getCurrentCompanyId();
        Long userId = auth.getCurrentUserId();

        if (repo.existsByCodeAndCompanyId(dto.getCode(), companyId)) {
            throw new RuntimeException("Warehouse code already exists");
        }

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Warehouse wh = Warehouse.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .location(dto.getLocation())
                .status(dto.getStatus())
                .company(company)
                .createdByUser(user)
                .updatedByUser(user)
                .build();

        return toDTO(repo.save(wh));
    }

    // --------------------------
    // Update
    // --------------------------
    public WarehouseResponseDTO update(Long id, WarehouseUpdateDTO dto) {

        Warehouse wh = getWarehouseEntity(id);

        User user = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        wh.setName(dto.getName());
        wh.setLocation(dto.getLocation());
        wh.setStatus(dto.getStatus());
        wh.setUpdatedByUser(user);

        return toDTO(repo.save(wh));
    }

    // --------------------------
    // Get single
    // --------------------------
    public WarehouseResponseDTO get(Long id) {
        return toDTO(getWarehouseEntity(id));
    }

    // --------------------------
    // List
    // --------------------------
    public List<WarehouseResponseDTO> list() {
        return repo.findByCompanyId(auth.getCurrentCompanyId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // --------------------------
    // Delete
    // --------------------------
    public void delete(Long id) {
        Warehouse wh = getWarehouseEntity(id);
        repo.delete(wh);
    }

    // --------------------------
    // Helpers
    // --------------------------
    private Warehouse getWarehouseEntity(Long id) {
        return repo.findById(id)
                .filter(w -> w.getCompany().getId().equals(auth.getCurrentCompanyId()))
                .orElseThrow(() ->
                        new RuntimeException("Warehouse not found or access denied")
                );
    }

    private WarehouseResponseDTO toDTO(Warehouse wh) {
        return WarehouseResponseDTO.builder()
                .id(wh.getId())
                .code(wh.getCode())
                .name(wh.getName())
                .location(wh.getLocation())
                .status(wh.getStatus())
                .build();
    }
}
