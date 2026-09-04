package com.erp.dto.hrsettings;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class JobCodeRequestDTO {

    @NotBlank(message = "Job code is required")
    private String code;

    @NotBlank(message = "Job title is required")
    private String title;

    @NotBlank(message = "Job level is required")
    private String level;

    @NotBlank(message = "Salary grade is required")
    private String salaryGrade;

    @DecimalMin(value = "0.00", message = "Min salary must be zero or greater")
    private BigDecimal minSalary;

    @DecimalMin(value = "0.00", message = "Max salary must be zero or greater")
    private BigDecimal maxSalary;

    @NotNull(message = "Status is required")
    private Boolean active;

    // ── Defaults copied onto the current job when this code is assigned ──
    private Long departmentId;
    private Long divisionId;
    private String employmentCategory; // PERMANENT | CONTRACT | INTERN | CONSULTANT | TEMPORARY
    private String employmentType;     // FULL_TIME | PART_TIME
    private String workLocation;       // OFFICE | HYBRID | REMOTE
    private String workCity;
    private String workCountry;
}
