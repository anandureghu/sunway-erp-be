package com.erp.mapper;

import com.erp.domain.Employee;
import com.erp.domain.EmployeeCurrentJob;
import com.erp.dto.currentjob.EmployeeCurrentJobResponseDTO;

public class EmployeeCurrentJobMapper {

    public static EmployeeCurrentJobResponseDTO toDTO(EmployeeCurrentJob e) {

        EmployeeCurrentJobResponseDTO.JobInfo jobInfo = null;
        if (e.getJobCode() != null) {
            jobInfo = EmployeeCurrentJobResponseDTO.JobInfo.builder()
                    .id(e.getJobCode().getId())
                    .code(e.getJobCode().getCode())
                    .title(e.getJobCode().getTitle())
                    .level(e.getJobCode().getLevel())
                    .salaryGrade(e.getJobCode().getSalaryGrade())
                    .minSalary(e.getJobCode().getMinSalary())
                    .maxSalary(e.getJobCode().getMaxSalary())
                    .build();
        }

        EmployeeCurrentJobResponseDTO.DepartmentInfo departmentInfo = null;
        if (e.getDepartment() != null) {
            var dept = e.getDepartment();
            var division = e.getDivision();
            departmentInfo = EmployeeCurrentJobResponseDTO.DepartmentInfo.builder()
                    .id(dept.getId())
                    .code(dept.getDepartmentCode())
                    .name(dept.getDepartmentName())
                    .divisionId(division != null ? division.getId() : null)
                    .divisionCode(division != null ? division.getCode() : null)
                    .divisionName(division != null ? division.getName() : null)
                    .build();
        }

        Long employeeId = e.getEmployee() != null ? e.getEmployee().getId() : null;

        Employee rm = e.getReportingManager();
        Long reportingManagerId = rm != null ? rm.getId() : null;
        String reportingManagerName = rm != null
                ? joinNonBlank(rm.getFirstName(), rm.getLastName())
                : null;
        String reportingManagerEmployeeNo = rm != null ? rm.getEmployeeNo() : null;

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
                .employmentCategory(e.getEmploymentCategory())
                .employmentType(e.getEmploymentType())
                .reportingManagerId(reportingManagerId)
                .reportingManagerName(reportingManagerName)
                .reportingManagerEmployeeNo(reportingManagerEmployeeNo)
                .contractStartDate(e.getContractStartDate())
                .contractEndDate(e.getContractEndDate())
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
        e.setEmploymentCategory(d.getEmploymentCategory());
        e.setEmploymentType(d.getEmploymentType());
        e.setContractStartDate(d.getContractStartDate());
        e.setContractEndDate(d.getContractEndDate());
        // reportingManager is set in the service (needs repository lookup)
    }

    private static String joinNonBlank(String a, String b) {
        StringBuilder sb = new StringBuilder();
        if (a != null && !a.isBlank()) sb.append(a.trim());
        if (b != null && !b.isBlank()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(b.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
