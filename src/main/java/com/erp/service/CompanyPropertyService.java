package com.erp.service;

import com.erp.domain.CompanyProperty;
import com.erp.domain.Employee;
import com.erp.dto.property.CompanyPropertyRequestDTO;
import com.erp.dto.property.CompanyPropertyResponseDTO;
import com.erp.repo.CompanyPropertyRepository;
import com.erp.repo.EmployeeRepository;
import com.erp.security.context.AuthContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
@RequiredArgsConstructor
public class CompanyPropertyService {

    private final EmployeeRepository employeeRepo;
    private final CompanyPropertyRepository propertyRepo;
    private final AuthContext authContext;

    /* ================= CREATE ================= */
    @Transactional
    public CompanyPropertyResponseDTO createProperty(
            Long employeeId,
            CompanyPropertyRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        assertSameTenant(employee);

        // 🔒 Prevent duplicate asset for same employee
        if (propertyRepo.existsByEmployeeIdAndItemCode(employeeId, dto.getItemCode())) {
            throw new RuntimeException("Property already assigned to this employee");
        }

        CompanyProperty p = new CompanyProperty();
        p.setEmployee(employee);

        mapAndValidate(p, dto);

        CompanyProperty saved = propertyRepo.save(p);
        return toDTO(saved);
    }

    /* ================= UPDATE ================= */
    @Transactional
    public CompanyPropertyResponseDTO updateProperty(
            Long employeeId,
            Long propertyId,
            CompanyPropertyRequestDTO dto) {

        CompanyProperty p = propertyRepo.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found"));
        assertSameTenant(p.getEmployee());

        if (!p.getEmployee().getId().equals(employeeId)) {
            throw new RuntimeException("Property does not belong to this employee");
        }

        mapAndValidate(p, dto);

        CompanyProperty saved = propertyRepo.save(p);
        return toDTO(saved);
    }

    /* ================= DELETE ================= */
    @Transactional
    public void deleteProperty(Long employeeId, Long propertyId) {

        CompanyProperty p = propertyRepo.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found"));
        assertSameTenant(p.getEmployee());

        if (!p.getEmployee().getId().equals(employeeId)) {
            throw new RuntimeException("Property does not belong to this employee");
        }

        propertyRepo.delete(p);
    }

    /* ================= GET ================= */
    public List<CompanyPropertyResponseDTO> getProperties(Long employeeId) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        assertSameTenant(employee);

        return propertyRepo.findByEmployeeId(employeeId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /* ================= TENANT GUARD ================= */
    private void assertSameTenant(Employee employee) {
        if ("SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole())) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        Long employeeCompanyId = employee != null && employee.getCompany() != null
                ? employee.getCompany().getId() : null;
        if (currentCompanyId == null || employeeCompanyId == null
                || !currentCompanyId.equals(employeeCompanyId)) {
            throw new AccessDeniedException("This company property belongs to a different company");
        }
    }

    /* ================= VALIDATION + MAPPING ================= */
    private void mapAndValidate(
            CompanyProperty p,
            CompanyPropertyRequestDTO dto) {

        if (dto.getItemCode() == null || dto.getItemCode().isBlank()) {
            throw new RuntimeException("Item Code is required");
        }

        if (dto.getItemName() == null || dto.getItemName().isBlank()) {
            throw new RuntimeException("Item Name is required");
        }

        if (dto.getDateGiven() == null) {
            throw new RuntimeException("Date Given is required");
        }

        if (dto.getItemStatus() == null) {
            throw new RuntimeException("Item Status is required");
        }

        // ================= STATUS RULES =================
        switch (dto.getItemStatus()) {

            case ASSIGNED -> {
                if (dto.getReturnDate() != null) {
                    throw new RuntimeException(
                            "Return Date must be empty when status is ASSIGNED"
                    );
                }
            }

            case RETURNED, LOST -> {
                if (dto.getReturnDate() == null) {
                    throw new RuntimeException(
                            "Return Date is required for status " + dto.getItemStatus()
                    );
                }
                if (dto.getReturnDate().isBefore(dto.getDateGiven())) {
                    throw new RuntimeException(
                            "Return Date cannot be before Date Given"
                    );
                }
            }

            case DAMAGED -> {
                if (dto.getReturnDate() != null &&
                        dto.getReturnDate().isBefore(dto.getDateGiven())) {
                    throw new RuntimeException(
                            "Return Date cannot be before Date Given"
                    );
                }
            }
        }

        // ================= SAFE MAPPING =================
        p.setItemCode(dto.getItemCode());
        p.setItemName(dto.getItemName());
        p.setItemStatus(dto.getItemStatus());
        p.setDescription(dto.getDescription());
        p.setDateGiven(dto.getDateGiven());
        p.setReturnDate(dto.getReturnDate());
    }

    /* ================= DTO ================= */
    private CompanyPropertyResponseDTO toDTO(CompanyProperty p) {

        CompanyPropertyResponseDTO dto = new CompanyPropertyResponseDTO();
        dto.setId(p.getId());
        dto.setItemCode(p.getItemCode());
        dto.setItemName(p.getItemName());
        dto.setItemStatus(p.getItemStatus());
        dto.setDescription(p.getDescription());
        dto.setDateGiven(p.getDateGiven());
        dto.setReturnDate(p.getReturnDate());

        return dto;
    }
}
