package com.erp.mapper;

import com.erp.domain.EmployeeExperience;
import com.erp.dto.currentjob.EmployeeExperienceRequestDTO;
import com.erp.dto.currentjob.EmployeeExperienceResponseDTO;

public class EmployeeExperienceMapper {

    // ================= ENTITY → DTO =================
    public static EmployeeExperienceResponseDTO toDTO(EmployeeExperience e) {

        EmployeeExperienceResponseDTO dto = new EmployeeExperienceResponseDTO();
        dto.setId(e.getId());
        dto.setEmployeeId(e.getEmployee().getId());
        dto.setCompanyName(e.getCompanyName());
        dto.setJobTitle(e.getJobTitle());
        dto.setLastDateWorked(e.getLastDateWorked()); // LocalDate
        dto.setNumberOfYears(e.getNumberOfYears());
        dto.setCompanyAddress(e.getCompanyAddress());
        dto.setNotes(e.getNotes());

        return dto;
    }

    // ================= DTO → ENTITY =================
    public static void updateEntity(
            EmployeeExperience e,
            EmployeeExperienceRequestDTO d
    ) {
        e.setCompanyName(d.getCompanyName());
        e.setJobTitle(d.getJobTitle());
        e.setLastDateWorked(d.getLastDateWorked()); // LocalDate
        e.setNumberOfYears(d.getNumberOfYears());
        e.setCompanyAddress(d.getCompanyAddress());
        e.setNotes(d.getNotes());
    }
}
