package com.erp.service.inventory;

import com.erp.domain.Employee;
import com.erp.domain.User;
import com.erp.domain.hr.Company;
import com.erp.domain.inventory.Warehouse;
import com.erp.dto.inventory.WarehouseCreateDTO;
import com.erp.dto.inventory.WarehouseResponseDTO;
import com.erp.dto.inventory.WarehouseUpdateDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.repo.hr.CompanyRepository;
import com.erp.repo.inventory.ItemWarehouseStockRepository;
import com.erp.repo.inventory.WarehouseRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.DocumentSequenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class WarehouseService {

    private final WarehouseRepository repo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final ItemWarehouseStockRepository warehouseStockRepo;
    private final AuthContext auth;
    private final DocumentSequenceService documentSequenceService;

    public WarehouseService(
            WarehouseRepository repo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            EmployeeRepository employeeRepo,
            ItemWarehouseStockRepository warehouseStockRepo,
            AuthContext auth,
            DocumentSequenceService documentSequenceService
    ) {
        this.repo = repo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.warehouseStockRepo = warehouseStockRepo;
        this.auth = auth;
        this.documentSequenceService = documentSequenceService;
    }

    // --------------------------
    // Create
    // --------------------------
    public WarehouseResponseDTO create(WarehouseCreateDTO dto) {

        Long companyId = auth.getCurrentCompanyId();
        Long userId = auth.getCurrentUserId();

        String code = dto.getCode() != null ? dto.getCode().trim() : "";
        if (code.isBlank()) {
            code = documentSequenceService.generateNext("WH");
        }

        if (repo.existsByCodeAndCompanyId(code, companyId)) {
            throw new RuntimeException("Warehouse code already exists");
        }

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Company not found"));

        User user = userRepo.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User manager = resolveManagerUser(dto.getManager(), companyId);

        Warehouse wh = Warehouse.builder()
                .code(code)
                .name(dto.getName())
                .warehouseType(normalizeWarehouseType(dto.getWarehouseType()))
                .capacity(dto.getCapacity())
                .status(normalizeStatus(dto.getStatus()))
                .company(company)
                .street(dto.getStreet())
                .city(dto.getCity())
                .country(dto.getCountry())
                .pin(dto.getPin())
                .phone(dto.getPhone())
                .manager(manager)
                .contactPersonName(dto.getContactPersonName())
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
        Long companyId = wh.getCompany().getId();

        User user = userRepo.findById(auth.getCurrentUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        User manager = dto.getManager() == null
                ? null
                : resolveManagerUser(dto.getManager(), companyId);

        wh.setName(dto.getName());
        wh.setWarehouseType(normalizeWarehouseType(dto.getWarehouseType()));
        wh.setCapacity(dto.getCapacity());
        wh.setStatus(normalizeStatus(dto.getStatus()));
        wh.setUpdatedByUser(user);
        wh.setCity(dto.getCity());
        wh.setStreet(dto.getStreet());
        wh.setPin(dto.getPin());
        wh.setPhone(dto.getPhone());
        wh.setCountry(dto.getCountry());
        wh.setContactPersonName(dto.getContactPersonName());
        wh.setManager(manager);

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
        Long companyId = auth.getCurrentCompanyId();
        return repo.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .map(wh -> toDTO(clearCrossTenantManager(wh, companyId)))
                .toList();
    }

    // --------------------------
    // Delete
    // --------------------------
    public void delete(Long id) {
        Warehouse wh = getWarehouseEntity(id);
        assertWarehouseHasNoStock(wh);
        repo.delete(wh);
    }

    private void assertWarehouseHasNoStock(Warehouse wh) {
        int onHand = warehouseStockRepo.findByWarehouseId(wh.getId()).stream()
                .mapToInt(row -> row.getQuantityOnHand() == null ? 0 : row.getQuantityOnHand())
                .sum();
        int reserved = warehouseStockRepo.findByWarehouseId(wh.getId()).stream()
                .mapToInt(row -> row.getReserved() == null ? 0 : row.getReserved())
                .sum();
        if (onHand > 0 || reserved > 0) {
            throw new IllegalArgumentException(
                    "Cannot delete warehouse while it has stock on hand or reserved quantity. "
                            + "Move or adjust stock to zero first.");
        }
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

    /**
     * Resolves a warehouse manager User that must have an Employee membership in the
     * given company. Accepts a user id (preferred) or an employee id for older clients.
     */
    private User resolveManagerUser(Long managerRef, Long companyId) {
        if (managerRef == null) {
            return null;
        }
        if (companyId == null) {
            throw new RuntimeException("Company not found");
        }

        if (employeeRepo.existsByUser_IdAndCompany_Id(managerRef, companyId)) {
            return userRepo.findById(managerRef)
                    .orElseThrow(() -> new RuntimeException("User for manager not found"));
        }

        Employee asEmployee = employeeRepo.findById(managerRef).orElse(null);
        if (asEmployee != null
                && asEmployee.getCompany() != null
                && companyId.equals(asEmployee.getCompany().getId())
                && asEmployee.getUser() != null) {
            return asEmployee.getUser();
        }

        throw new RuntimeException("Manager must belong to the current company");
    }

    /** Persistently clears a manager FK that points to a user outside this warehouse's tenant. */
    private Warehouse clearCrossTenantManager(Warehouse wh, Long companyId) {
        User manager = wh.getManager();
        if (manager == null || companyId == null) {
            return wh;
        }
        if (employeeRepo.existsByUser_IdAndCompany_Id(manager.getId(), companyId)) {
            return wh;
        }
        wh.setManager(null);
        return repo.save(wh);
    }

    private WarehouseResponseDTO toDTO(Warehouse wh) {
        User manager = wh.getManager();
        Long companyId = wh.getCompany() != null ? wh.getCompany().getId() : null;
        boolean managerInTenant = manager != null
                && companyId != null
                && employeeRepo.existsByUser_IdAndCompany_Id(manager.getId(), companyId);

        return WarehouseResponseDTO.builder()
                .id(wh.getId())
                .code(wh.getCode())
                .name(wh.getName())
                .warehouseType(wh.getWarehouseType())
                .capacity(wh.getCapacity())
                .status(wh.getStatus())
                .street(wh.getStreet())
                .city(wh.getCity())
                .country(wh.getCountry())
                .pin(wh.getPin())
                .phone(wh.getPhone())
                .managerId(managerInTenant ? manager.getId() : null)
                .managerName(managerInTenant ? manager.getFullName() : null)
                .contactPersonName(wh.getContactPersonName())
                .build();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "active";
        }
        String n = status.trim().toLowerCase();
        if (n.startsWith("inact") || n.equals("no") || n.equals("0") || n.equals("disabled") || n.equals("false")) {
            return "inactive";
        }
        return "active";
    }

    /**
     * Maps spreadsheet type labels onto stored warehouse_type values.
     */
    static String normalizeWarehouseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String n = raw.trim().toLowerCase().replaceAll("[^a-z0-9]+", "");
        return switch (n) {
            case "main", "general", "mainstore", "primary" -> "MAIN";
            case "branch", "satellite" -> "BRANCH";
            case "transit", "staging" -> "TRANSIT";
            case "coldstorage", "cold", "refrigerated" -> "COLD_STORAGE";
            case "returns", "return" -> "RETURNS";
            case "hazardous", "hazmat", "chemical" -> "HAZARDOUS";
            case "ppesafety", "ppe", "safety" -> "PPE_SAFETY";
            case "sitestore", "site", "jobsite" -> "SITE_STORE";
            case "other" -> "OTHER";
            default -> raw.trim();
        };
    }
}
