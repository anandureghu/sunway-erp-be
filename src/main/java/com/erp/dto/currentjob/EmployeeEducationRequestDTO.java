package com.erp.dto.currentjob;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class EmployeeEducationRequestDTO {
    private String schoolName;
    private String schoolAddress;
    private String degreeEarned;
    private String major;
    private Integer yearGraduated;
    private String awards;
    private String notes;

}
