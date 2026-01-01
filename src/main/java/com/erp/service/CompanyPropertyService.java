package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.CompanyProperty;
import com.erp.domain.PropertyStatus;
import com.erp.dto.property.CompanyPropertyRequestDTO;
import com.erp.dto.property.CompanyPropertyResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.CompanyPropertyRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompanyPropertyService {

    private final EmployeeRepository employeeRepo;
    private final CompanyPropertyRepository propertyRepo;

    /* ================= CREATE ================= */
    @Transactional
    public void createProperty(Long employeeId, CompanyPropertyRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        CompanyProperty p = new CompanyProperty();
        p.setEmployee(employee);

        mapAndValidate(p, dto);

        propertyRepo.save(p);
    }

    /* ================= UPDATE ================= */
    @Transactional
    public void updateProperty(Long employeeId,
                               Long propertyId,
                               CompanyPropertyRequestDTO dto) {

        CompanyProperty p = propertyRepo.findById(propertyId)
                .orElseThrow(() -> new RuntimeException("Property not found"));

        if (!p.getEmployee().getId().equals(employeeId)) {
            throw new RuntimeException("Property does not belong to this employee");
        }

        mapAndValidate(p, dto);
        propertyRepo.save(p);
    }

    /* ================= GET ================= */
    public List<CompanyPropertyResponseDTO> getProperties(Long employeeId) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        return propertyRepo.findByEmployee(employee)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    /* ================= VALIDATION + MAPPING ================= */
    private void mapAndValidate(CompanyProperty p,
                                CompanyPropertyRequestDTO dto) {

        if (dto.getDateGiven() == null) {
            throw new RuntimeException("Date Given is required");
        }

        if (dto.getItemStatus() == null) {
            throw new RuntimeException("Item Status is required");
        }

        // 🔒 STATUS-BASED DATE RULES
        if (dto.getItemStatus() == PropertyStatus.ISSUED) {

            if (dto.getReturnDate() != null) {
                throw new RuntimeException(
                        "Return Date must be empty when status is ISSUED"
                );
            }

        } else if (dto.getItemStatus() == PropertyStatus.RETURNED) {

            if (dto.getReturnDate() == null) {
                throw new RuntimeException(
                        "Return Date is required when status is RETURNED"
                );
            }

            if (dto.getReturnDate().isBefore(dto.getDateGiven())) {
                throw new RuntimeException(
                        "Return Date cannot be before Date Given"
                );
            }
        }

        // ✅ SAFE MAPPING
        p.setItemCode(dto.getItemCode());
        p.setItemName(dto.getItemName());
        p.setItemStatus(dto.getItemStatus()); // ENUM
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
        dto.setItemStatus(p.getItemStatus().name());
        dto.setDescription(p.getDescription());
        dto.setDateGiven(p.getDateGiven());
        dto.setReturnDate(p.getReturnDate());

        return dto;
    }
}
