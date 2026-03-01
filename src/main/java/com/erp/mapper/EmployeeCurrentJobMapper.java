package com.erp.mapper;

import com.erp.domain.EmployeeCurrentJob;
import com.erp.dto.currentjob.EmployeeCurrentJobResponseDTO;

public class EmployeeCurrentJobMapper {

    public static EmployeeCurrentJobResponseDTO toDTO(EmployeeCurrentJob e) {

        // ✅ Null-safe JobInfo
        EmployeeCurrentJobResponseDTO.JobInfo jobInfo = null;
        if (e.getJobCode() != null) {
            jobInfo = EmployeeCurrentJobResponseDTO.JobInfo.builder()
                    .id(e.getJobCode().getId())
                    .code(e.getJobCode().getCode())
                    .title(e.getJobCode().getTitle())
                    .level(e.getJobCode().getLevel())
                    .grade(e.getJobCode().getGrade())
                    .build();
        }

        // ✅ Null-safe DepartmentInfo
        EmployeeCurrentJobResponseDTO.DepartmentInfo departmentInfo = null;
        if (e.getDepartment() != null) {
            departmentInfo = EmployeeCurrentJobResponseDTO.DepartmentInfo.builder()
                    .id(e.getDepartment().getId())
                    .code(e.getDepartment().getDepartmentCode())
                    .name(e.getDepartment().getDepartmentName())
                    .build();
        }

        // ✅ Null-safe employeeId
        Long employeeId = e.getEmployee() != null ? e.getEmployee().getId() : null;

        return EmployeeCurrentJobResponseDTO.builder()
                .id(e.getId())
                .employeeId(employeeId)
                .job(jobInfo)
                .department(departmentInfo)
                .workLocation(e.getWorkLocation())
                .workCity(e.getWorkCity())
                .workCountry(e.getWorkCountry())
                .effectiveFrom(e.getEffectiveFrom())
                .startDate(e.getStartDate())
                .expectedEndDate(e.getExpectedEndDate())
                .build();
    }

    public static void updateEntity(EmployeeCurrentJob e,
                                    com.erp.dto.currentjob.EmployeeCurrentJobRequestDTO d) {

        e.setEffectiveFrom(d.getEffectiveFrom());
        e.setStartDate(d.getStartDate());
        e.setExpectedEndDate(d.getExpectedEndDate());
        e.setWorkLocation(d.getWorkLocation());
        e.setWorkCity(d.getWorkCity());
        e.setWorkCountry(d.getWorkCountry());
    }
}