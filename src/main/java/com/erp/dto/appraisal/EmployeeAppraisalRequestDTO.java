package com.erp.dto.appraisal;

import lombok.Data;

@Data
public class EmployeeAppraisalRequestDTO {

    /* =====================
       PERIOD
    ====================== */
    private String month;
    private Integer year;

    /* =====================
       JOB
    ====================== */
    private String jobCode;

    /* =====================
       KPIs
    ====================== */
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

    /* =====================
       COMMENTS
    ====================== */
    private String employeeComments;
    private String managerComments;

    /* =====================
       RATING / INCREMENT
    ====================== */
    private Integer rating;
    private Integer annualIncrement;
}
