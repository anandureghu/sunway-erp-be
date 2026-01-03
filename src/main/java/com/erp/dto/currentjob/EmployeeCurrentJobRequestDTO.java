package com.erp.dto.currentjob;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class EmployeeCurrentJobRequestDTO {

    private String jobCode;
    private String jobTitle;
    private String jobLevel;
    private String grade;
    private String departmentCode;
    private String departmentName;
    private LocalDate effectiveFrom;
    private LocalDate startDate;
    private LocalDate expectedEndDate;
}
