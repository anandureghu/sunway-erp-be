package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.ResidencePermit;
import com.erp.dto.immigration.ResidencePermitRequestDTO;
import com.erp.dto.immigration.ResidencePermitResponseDTO;
import com.erp.dto.file.FileCategory;
import com.erp.dto.file.FileUploadResult;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.ResidencePermitRepository;
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
public class ResidencePermitService {

    private final ResidencePermitRepository permitRepo;
    private final EmployeeRepository employeeRepo;
    private final AuthContext authContext;
    private final FileStorageService fileStorageService;

    /* ================= GET ================= */

    public ResidencePermitResponseDTO getByEmployee(Long employeeId) {
        ResidencePermit permit = permitRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Residence permit not found"));
        assertSameTenant(permit.getEmployee());
        return toDTO(permit);
    }

    /* ================= CREATE ================= */

    public ResidencePermitResponseDTO create(ResidencePermitRequestDTO dto) {

        validatePermitDates(dto);

        if (permitRepo.existsByEmployeeId(dto.getEmployeeId())) {
            throw new RuntimeException("Residence permit already exists for employee");
        }

        Employee employee = employeeRepo.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        assertSameTenant(employee);

        ResidencePermit permit = ResidencePermit.builder()
                .employee(employee)
                .company(employee.getCompany())
                .permitIdNumber(dto.getPermitIdNumber())   // ✅ ONLY CHANGE
                .visaType(dto.getVisaType())
                .durationType(dto.getDurationType())
                .visaDuration(dto.getVisaDuration())
                .nationality(dto.getNationality())
                .occupation(dto.getOccupation())
                .issuePlace(dto.getIssuePlace())
                .issueAuthority(dto.getIssueAuthority())
                .visaStatus(dto.getVisaStatus())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .build();

        permitRepo.save(permit);
        return toDTO(permit);
    }

    /* ================= UPDATE ================= */

    public ResidencePermitResponseDTO update(ResidencePermitRequestDTO dto) {

        validatePermitDates(dto);

        ResidencePermit permit = permitRepo.findByEmployeeId(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Residence permit not found"));
        assertSameTenant(permit.getEmployee());

        permit.setPermitIdNumber(dto.getPermitIdNumber()); // ✅ ONLY CHANGE
        permit.setVisaType(dto.getVisaType());
        permit.setDurationType(dto.getDurationType());
        permit.setVisaDuration(dto.getVisaDuration());
        permit.setNationality(dto.getNationality());
        permit.setOccupation(dto.getOccupation());
        permit.setIssuePlace(dto.getIssuePlace());
        permit.setIssueAuthority(dto.getIssueAuthority());
        permit.setVisaStatus(dto.getVisaStatus());
        permit.setStartDate(dto.getStartDate());
        permit.setEndDate(dto.getEndDate());

        permitRepo.save(permit);
        return toDTO(permit);
    }

    /* ================= DELETE ================= */

    public void deleteByEmployee(Long employeeId) {

        ResidencePermit permit = permitRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Residence permit not found"));
        assertSameTenant(permit.getEmployee());

        permitRepo.delete(permit);
    }

    /* ================= DOCUMENT UPLOAD ================= */

    public ResidencePermitResponseDTO uploadDocument(Long employeeId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        ResidencePermit permit = permitRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Residence permit not found"));
        assertSameTenant(permit.getEmployee());

        FileUploadResult result = fileStorageService.upload(
                file, FileCategory.RESIDENCE_PERMIT_DOCUMENT, employeeId.toString(), false,
                authContext.getCurrentCompanyId());
        permit.setDocumentPath(result.getBlobPath());
        permitRepo.save(permit);
        return toDTO(permit);
    }

    /* ================= TENANT GUARD ================= */

    private void assertSameTenant(Employee employee) {
        if ("SUPER_ADMIN".equalsIgnoreCase(authContext.getCurrentUserRole())) return;
        Long currentCompanyId = authContext.getCurrentCompanyId();
        Long employeeCompanyId = employee != null && employee.getCompany() != null
                ? employee.getCompany().getId() : null;
        if (currentCompanyId == null || employeeCompanyId == null
                || !currentCompanyId.equals(employeeCompanyId)) {
            throw new AccessDeniedException("This residence permit belongs to a different company");
        }
    }

    /* ================= DATE VALIDATION ================= */

    private void validatePermitDates(ResidencePermitRequestDTO dto) {

        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }

        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
    }

    /* ================= MAPPER ================= */

    private ResidencePermitResponseDTO toDTO(ResidencePermit p) {
        Employee emp = p.getEmployee();
        return ResidencePermitResponseDTO.builder()
                .id(p.getId())
                .employeeId(emp.getId())
                .employeeCode(emp.getEmployeeNo())
                .employeeName(((emp.getFirstName() == null ? "" : emp.getFirstName())
                        + " " + (emp.getLastName() == null ? "" : emp.getLastName())).trim())
                .permitIdNumber(p.getPermitIdNumber())
                .visaType(p.getVisaType())
                .durationType(p.getDurationType())
                .visaDuration(p.getVisaDuration())
                .nationality(p.getNationality())
                .occupation(p.getOccupation())
                .issuePlace(p.getIssuePlace())
                .issueAuthority(p.getIssueAuthority())
                .visaStatus(p.getVisaStatus())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .documentUrl(p.getDocumentPath() != null
                        ? fileStorageService.getPrivateSasUrl(p.getDocumentPath()) : null)
                .build();
    }
}
