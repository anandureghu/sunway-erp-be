package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeContactInfo;
import com.erp.domain.security.AppModule;
import com.erp.dto.contact.EmployeeContactInfoRequestDTO;
import com.erp.dto.contact.EmployeeContactInfoResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.UserRepository;
import com.erp.repo.contact.EmployeeContactInfoRepository;
import com.erp.security.guard.EmployeeAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeContactInfoService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeContactInfoRepository contactInfoRepo;
    private final EmployeeAccessGuard employeeAccessGuard;
    private final UserRepository userRepository;

    // ======================================================
    // GET CONTACT INFO
    // ======================================================
    public EmployeeContactInfoResponseDTO getContactInfo(Long employeeId) {

        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        employeeAccessGuard.assertCanRead(employee, AppModule.EMPLOYEE_PROFILE);

        EmployeeContactInfo contactInfo = contactInfoRepo
                .findByEmployeeId(employeeId)
                .orElse(null);

        if (contactInfo == null) {
            return EmployeeContactInfoResponseDTO.builder()
                    .email(null)
                    .phone(null)
                    .altPhone(null)
                    .notes(null)
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

        employeeAccessGuard.assertCanWrite(employee, AppModule.EMPLOYEE_PROFILE);

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

        // Login and every other feature read User.email, not this table — keep them
        // in sync so editing the contact email here doesn't silently diverge from it.
        if (employee.getUser() != null && !dto.getEmail().equalsIgnoreCase(employee.getUser().getEmail())) {
            userRepository.findByEmailIgnoreCase(dto.getEmail())
                    .filter(existing -> !existing.getId().equals(employee.getUser().getId()))
                    .ifPresent(existing -> {
                        throw new RuntimeException("Email already in use");
                    });
            employee.getUser().setEmail(dto.getEmail());
            userRepository.save(employee.getUser());
        }

        contactInfo.setEmail(dto.getEmail());
        contactInfo.setPhone(dto.getPhone());
        contactInfo.setAltPhone(dto.getAltPhone());
        contactInfo.setNotes(dto.getNotes());

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
                .notes(contactInfo.getNotes())
                .build();
    }
}
