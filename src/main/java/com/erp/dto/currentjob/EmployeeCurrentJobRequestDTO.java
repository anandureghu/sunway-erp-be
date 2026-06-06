package com.erp.dto.currentjob;

import com.erp.domain.enums.EmploymentCategory;
import com.erp.domain.enums.EmploymentType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class EmployeeCurrentJobRequestDTO {

    @NotNull(message = "Job Code is required")
    private Long jobCodeId;

    @NotNull(message = "Department is required")
    private Long departmentId;

    private Long divisionId;

    private String workLocation;
    private String workCity;
    private String workCountry;

    @NotNull(message = "Effective from date is required")
    private LocalDate effectiveFrom;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate expectedEndDate;

    private EmploymentCategory employmentCategory;
    private EmploymentType employmentType;
    private Long reportingManagerId;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
}
