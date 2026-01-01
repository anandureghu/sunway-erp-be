package com.erp.dto.appraisal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeeAppraisalRequestDTO {

    // Identifiers (set in controller)
    private Long employeeId;


    // Appraisal form fields
    private String jobCode;
    private String employeeComments;
    private String managerComments;
}
