package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.Passport;
import com.erp.dto.immigration.PassportRequestDTO;
import com.erp.dto.immigration.PassportResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.PassportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PassportService {

    private final PassportRepository passportRepo;
    private final EmployeeRepository employeeRepo;

    // ---------------- GET ----------------
    public PassportResponseDTO getByEmployee(Long employeeId) {
        Passport passport = passportRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Passport not found"));
        return toDTO(passport);
    }

    // ---------------- CREATE ----------------
    public PassportResponseDTO create(PassportRequestDTO dto) {

        validatePassportDates(dto);

        if (passportRepo.existsByEmployeeId(dto.getEmployeeId())) {
            throw new RuntimeException("Passport already exists for employee");
        }

        Employee employee = employeeRepo.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        Passport passport = Passport.builder()
                .employee(employee)
                .passportNo(dto.getPassportNo())
                .nameAsPassport(dto.getNameAsPassport())
                .issueCountry(dto.getIssueCountry())
                .nationality(dto.getNationality())
                .issueDate(dto.getIssueDate())
                .expiryDate(dto.getExpiryDate())
                .build();

        passportRepo.save(passport);
        return toDTO(passport);
    }

    // ---------------- UPDATE ----------------
    public PassportResponseDTO update(PassportRequestDTO dto) {

        validatePassportDates(dto);

        Passport passport = passportRepo.findByEmployeeId(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Passport not found"));

        passport.setPassportNo(dto.getPassportNo());
        passport.setNameAsPassport(dto.getNameAsPassport());
        passport.setIssueCountry(dto.getIssueCountry());
        passport.setNationality(dto.getNationality());
        passport.setIssueDate(dto.getIssueDate());
        passport.setExpiryDate(dto.getExpiryDate());

        passportRepo.save(passport);

        return toDTO(passport);
    }

    // ---------------- DELETE ----------------
    public void deleteByEmployee(Long employeeId) {

        Passport passport = passportRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Passport not found"));

        passportRepo.delete(passport);
    }

    // ---------------- VALIDATION ----------------
    private void validatePassportDates(PassportRequestDTO dto) {
        if (dto.getIssueDate() == null || dto.getExpiryDate() == null) {
            throw new IllegalArgumentException("Issue date and expiry date are required");
        }

        if (dto.getIssueDate().isAfter(dto.getExpiryDate())) {
            throw new IllegalArgumentException(
                    "Issue date must be before expiry date"
            );
        }
    }

    // ---------------- MAPPER ----------------
    private PassportResponseDTO toDTO(Passport p) {
        return PassportResponseDTO.builder()
                .id(p.getId())
                .employeeId(p.getEmployee().getId())
                .passportNo(p.getPassportNo())
                .nameAsPassport(p.getNameAsPassport())
                .issueCountry(p.getIssueCountry())
                .nationality(p.getNationality())
                .issueDate(p.getIssueDate())
                .expiryDate(p.getExpiryDate())
                .build();
    }
}
