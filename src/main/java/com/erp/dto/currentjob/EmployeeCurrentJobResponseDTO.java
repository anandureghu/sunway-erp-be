package com.erp.dto.currentjob;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
public class EmployeeCurrentJobResponseDTO {

    private Long id;
    private Long employeeId;

    private JobInfo job;
    private DepartmentInfo department;

    private String workLocation;
    private String workCity;
    private String workCountry;

    private LocalDate effectiveFrom;
    private LocalDate startDate;
    private LocalDate expectedEndDate;

    @Getter
    @Setter
    @Builder
    public static class JobInfo {
        private Long id;
        private String code;
        private String title;
        private String level;
        private String grade;
    }

    @Getter
    @Setter
    @Builder
    public static class DepartmentInfo {
        private Long id;
        private String code;
        private String name;
    }
}