package com.erp.dto.appraisal;

import lombok.Data;
import java.util.List;

@Data
public class EmployeeAppraisalRequestDTO {

    private Integer year;

    private List<EmployeeGoalDTO> goals;

    private String employeeComments;
    private String managerComments;

    private String employeeAcknowledge;
    private String employeeRebuttal;
}