package com.erp.dto.currentjob;

import com.erp.domain.enums.EmploymentCategory;
import com.erp.domain.enums.EmploymentType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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

    private EmploymentCategory employmentCategory;
    private EmploymentType employmentType;

    private Long reportingManagerId;
    private String reportingManagerName;
    private String reportingManagerEmployeeNo;

    private LocalDate contractStartDate;
    private LocalDate contractEndDate;

    @Getter
    @Setter
    @Builder
    public static class JobInfo {
        private Long id;
        private String code;
        private String title;
        private String level;
        private String salaryGrade;
        private BigDecimal minSalary;
        private BigDecimal maxSalary;
    }

    @Getter
    @Setter
    @Builder
    public static class DepartmentInfo {
        private Long id;
        private String code;
        private String name;
        private Long divisionId;
        private String divisionCode;
        private String divisionName;
    }
}
