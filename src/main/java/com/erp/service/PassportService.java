package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.Passport;
import com.erp.dto.immigration.PassportRequestDTO;
import com.erp.dto.immigration.PassportResponseDTO;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.PassportRepository;
import com.erp.security.context.AuthContext;
import com.erp.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional
public class PassportService {

    private final PassportRepository passportRepo;
    private final EmployeeRepository employeeRepo;
    private final AuthContext authContext;
    private final FileStorageService fileStorageService;

    // ---------------- GET ----------------
    public PassportResponseDTO getByEmployee(Long employeeId) {
        // A new employee with no passport yet is a normal empty state, not an
        // error. Return null (HTTP 200 with empty body) so the tab shows a blank
        // form, instead of throwing a 500 that spams the error log every time the
        // Passport tab is opened for someone without a passport.
        return passportRepo.findByEmployeeId(employeeId)
                .map(passport -> {
                    assertSameTenant(passport.getEmployee());
                    return toDTO(passport);
                })
                .orElse(null);
    }

    // ---------------- CREATE ----------------
    public PassportResponseDTO create(PassportRequestDTO dto) {

        validatePassportDates(dto);

        if (passportRepo.existsByEmployeeId(dto.getEmployeeId())) {
            throw new RuntimeException("Passport already exists for employee");
        }

        Employee employee = employeeRepo.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        assertSameTenant(employee);

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
        assertSameTenant(passport.getEmployee());

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
        assertSameTenant(passport.getEmployee());

        passportRepo.delete(passport);
    }

    // ---------------- DOCUMENT UPLOAD ----------------
    public PassportResponseDTO uploadDocument(Long employeeId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        Passport passport = passportRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Passport not found"));
        assertSameTenant(passport.getEmployee());

        FileUploadResult result = fileStorageService.upload(
                file, FileCategory.PASSPORT_DOCUMENT, employeeId.toString(), false,
                authContext.getCurrentCompanyId());
        passport.setDocumentPath(result.getBlobPath());
        passportRepo.save(passport);
        return toDTO(passport);
    }

    // ---------------- TENANT GUARD ----------------
    private void assertSameTenant(Employee employee) {
        if ("SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole())) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        Long employeeCompanyId = employee != null && employee.getCompany() != null
                ? employee.getCompany().getId() : null;
        if (currentCompanyId == null || employeeCompanyId == null
                || !currentCompanyId.equals(employeeCompanyId)) {
            throw new AccessDeniedException("This passport belongs to a different company");
        }
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
                .documentUrl(p.getDocumentPath() != null
                        ? fileStorageService.getPrivateSasUrl(p.getDocumentPath()) : null)
                .build();
    }
}
