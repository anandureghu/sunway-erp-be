package com.erp.service.inventory;

import com.erp.domain.hr.Company;
import com.erp.domain.inventory.DispatchCarrier;
import com.erp.dto.inventory.DispatchCarrierCreateDTO;
import com.erp.dto.inventory.DispatchCarrierResponseDTO;
import com.erp.dto.inventory.DispatchCarrierUpdateDTO;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.DispatchCarrierRepository;
import com.erp.security.context.AuthContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DispatchCarrierService {

    private final DispatchCarrierRepository repo;
    private final CompanyRepository companyRepo;
    private final AuthContext auth;

    public DispatchCarrierService(
            DispatchCarrierRepository repo,
            CompanyRepository companyRepo,
            AuthContext auth
    ) {
        this.repo = repo;
        this.companyRepo = companyRepo;
        this.auth = auth;
    }

    public DispatchCarrierResponseDTO create(DispatchCarrierCreateDTO dto) {
        Long companyId = auth.getCurrentCompanyId();
        String name = normalizeRequired(dto.getName(), "Carrier name is required");
        if (repo.existsByNameIgnoreCaseAndCompanyId(name, companyId)) {
            throw new RuntimeException("Carrier name already exists");
        }
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));
        DispatchCarrier carrier = DispatchCarrier.builder()
                .name(name)
                .vehicleNumber(trimToNull(dto.getVehicleNumber()))
                .driverName(trimToNull(dto.getDriverName()))
                .driverPhone(trimToNull(dto.getDriverPhone()))
                .comments(trimToNull(dto.getComments()))
                .status(normalizeStatus(dto.getStatus()))
                .company(company)
                .build();
        return toDTO(repo.save(carrier));
    }

    public DispatchCarrierResponseDTO update(Long id, DispatchCarrierUpdateDTO dto) {
        Long companyId = auth.getCurrentCompanyId();
        DispatchCarrier carrier = getEntity(id, companyId);
        String name = normalizeRequired(dto.getName(), "Carrier name is required");
        if (repo.existsByNameIgnoreCaseAndCompanyIdAndIdNot(name, companyId, id)) {
            throw new RuntimeException("Carrier name already exists");
        }
        carrier.setName(name);
        carrier.setVehicleNumber(trimToNull(dto.getVehicleNumber()));
        carrier.setDriverName(trimToNull(dto.getDriverName()));
        carrier.setDriverPhone(trimToNull(dto.getDriverPhone()));
        carrier.setComments(trimToNull(dto.getComments()));
        carrier.setStatus(normalizeStatus(dto.getStatus()));
        return toDTO(repo.save(carrier));
    }

    public DispatchCarrierResponseDTO get(Long id) {
        return toDTO(getEntity(id, auth.getCurrentCompanyId()));
    }

    public List<DispatchCarrierResponseDTO> list() {
        return repo.findByCompanyIdOrderByNameAsc(auth.getCurrentCompanyId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public List<DispatchCarrierResponseDTO> listActive() {
        return list().stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .toList();
    }

    public void delete(Long id) {
        repo.delete(getEntity(id, auth.getCurrentCompanyId()));
    }

    private DispatchCarrier getEntity(Long id, Long companyId) {
        return repo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new RuntimeException("Carrier not found"));
    }

    private DispatchCarrierResponseDTO toDTO(DispatchCarrier carrier) {
        return DispatchCarrierResponseDTO.builder()
                .id(carrier.getId())
                .name(carrier.getName())
                .vehicleNumber(carrier.getVehicleNumber())
                .driverName(carrier.getDriverName())
                .driverPhone(carrier.getDriverPhone())
                .comments(carrier.getComments())
                .status(carrier.getStatus())
                .build();
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalized = status.trim().toUpperCase();
        if (!"ACTIVE".equals(normalized) && !"INACTIVE".equals(normalized)) {
            throw new RuntimeException("Carrier status must be ACTIVE or INACTIVE");
        }
        return normalized;
    }
}
