package com.erp.dto.appraisal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmployeeAppraisalResponseDTO {

    private Long id;            // MUST be named "id"
    private Long employeeId;
    private String month;
    private Integer year;

    // KPIs
    private String kpi1;
    private String review1;
    private String kpi2;
    private String review2;
    private String kpi3;
    private String review3;
    private String kpi4;
    private String review4;
    private String kpi5;
    private String review5;

    // Appraisal Form
    private String jobCode;
    private String employeeComments;
    private String managerComments;
}
