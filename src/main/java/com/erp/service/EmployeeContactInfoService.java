package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeContactInfo;
import com.erp.dto.contact.EmployeeContactInfoRequestDTO;
import com.erp.dto.contact.EmployeeContactInfoResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.contact.EmployeeContactInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeContactInfoService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeContactInfoRepository contactInfoRepo;

    // ======================================================
    // GET CONTACT INFO
    // ======================================================
    public EmployeeContactInfoResponseDTO getContactInfo(Long employeeId) {

        EmployeeContactInfo contactInfo = contactInfoRepo
                .findByEmployeeId(employeeId)
                .orElse(null);

        if (contactInfo == null) {
            return EmployeeContactInfoResponseDTO.builder()
                    .email(null)
                    .phone(null)
                    .altPhone(null)
                    .build();
        }

        return mapToResponse(contactInfo);
    }

    // ======================================================
    // CREATE / UPDATE CONTACT INFO
    // ======================================================
    public EmployeeContactInfoResponseDTO saveOrUpdateContactInfo(
            Long employeeId,
            EmployeeContactInfoRequestDTO dto) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new RuntimeException("Email is required");
        }

        EmployeeContactInfo contactInfo = contactInfoRepo
                .findByEmployeeId(employeeId)
                .orElseGet(() ->
                        EmployeeContactInfo.builder()
                                .employee(employee)
                                .build()
                );

        contactInfo.setEmail(dto.getEmail());
        contactInfo.setPhone(dto.getPhone());
        contactInfo.setAltPhone(dto.getAltPhone());

        contactInfo = contactInfoRepo.save(contactInfo);

        return mapToResponse(contactInfo);
    }

    // ======================================================
    // MAPPER
    // ======================================================
    private EmployeeContactInfoResponseDTO mapToResponse(
            EmployeeContactInfo contactInfo) {

        return EmployeeContactInfoResponseDTO.builder()
                .email(contactInfo.getEmail())
                .phone(contactInfo.getPhone())
                .altPhone(contactInfo.getAltPhone())
                .build();
    }
}
