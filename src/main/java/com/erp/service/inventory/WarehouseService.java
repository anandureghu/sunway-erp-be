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
    private final EmployeeRepository employeeRepo;
    private final AuthContext auth;

    public WarehouseService(
            WarehouseRepository repo,
            CompanyRepository companyRepo,
            UserRepository userRepo,
            EmployeeRepository employeeRepo,
            AuthContext auth
    ) {
        this.repo = repo;
        this.companyRepo = companyRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
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

        User manager = resolveManagerUser(dto.getManager(), companyId);

        Warehouse wh = Warehouse.builder()
                .code(dto.getCode())
                .name(dto.getName())
                .status(dto.getStatus())
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
        wh.setStatus(dto.getStatus());
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
}
