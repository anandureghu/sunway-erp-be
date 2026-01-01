package com.erp.dto.currentjob;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class EmployeeExperienceRequestDTO {

    @NotBlank
    private String companyName;

    @NotBlank
    private String jobTitle;

    @NotNull
    private LocalDate lastDateWorked;

    @Min(0)
    private Integer numberOfYears;

    private String companyAddress;
    private String notes;
}
