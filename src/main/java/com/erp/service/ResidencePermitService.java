package com.erp.service;

import com.erp.domain.Employee;
import com.erp.domain.ResidencePermit;
import com.erp.dto.immigration.ResidencePermitRequestDTO;
import com.erp.dto.immigration.ResidencePermitResponseDTO;
import com.erp.repo.EmployeeRepository;
import com.erp.repo.ResidencePermitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ResidencePermitService {

    private final ResidencePermitRepository permitRepo;
    private final EmployeeRepository employeeRepo;

    // ---------------- GET ----------------
    public ResidencePermitResponseDTO getByEmployee(Long employeeId) {
        ResidencePermit permit = permitRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Residence permit not found"));
        return toDTO(permit);
    }

    // ---------------- CREATE ----------------
    public ResidencePermitResponseDTO create(ResidencePermitRequestDTO dto) {

        validatePermitDates(dto);

        if (permitRepo.existsByEmployeeId(dto.getEmployeeId())) {
            throw new RuntimeException("Residence permit already exists for employee");
        }

        Employee employee = employeeRepo.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        ResidencePermit permit = ResidencePermit.builder()
                .employee(employee)
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

    // ---------------- UPDATE ----------------
    public ResidencePermitResponseDTO update(ResidencePermitRequestDTO dto) {

        validatePermitDates(dto);

        ResidencePermit permit = permitRepo.findByEmployeeId(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Residence permit not found"));

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

    // ---------------- DELETE ----------------
    public void deleteByEmployee(Long employeeId) {

        ResidencePermit permit = permitRepo.findByEmployeeId(employeeId)
                .orElseThrow(() -> new RuntimeException("Residence permit not found"));

        permitRepo.delete(permit);
    }

    // ---------------- DATE VALIDATION ----------------
    private void validatePermitDates(ResidencePermitRequestDTO dto) {

        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }

        if (dto.getStartDate().isAfter(dto.getEndDate())) {
            throw new IllegalArgumentException(
                    "Start date must be before end date"
            );
        }
    }

    // ---------------- MAPPER ----------------
    private ResidencePermitResponseDTO toDTO(ResidencePermit p) {
        return ResidencePermitResponseDTO.builder()
                .id(p.getId())
                .employeeId(p.getEmployee().getId())
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
                .build();
    }
}
