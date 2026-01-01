package com.erp.dto.currentjob;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class EmployeeExperienceResponseDTO {

    private Long id;
    private Long employeeId;

    private String companyName;
    private String jobTitle;
    private LocalDate lastDateWorked;
    private Integer numberOfYears;
    private String companyAddress;
    private String notes;
}
