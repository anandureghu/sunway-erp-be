package com.erp.dto.appraisal;

import lombok.Data;

@Data
public class EmployeeAppraisalRequestDTO {

    /* ================= PERIOD ================= */
    private String month;
    private Integer year;

    /* ================= JOB ================= */
    private String jobCode;

    /* ================= KPI 1 ================= */
    private String kpi1;
    private String review1;
    private Integer rating1;

    /* ================= KPI 2 ================= */
    private String kpi2;
    private String review2;
    private Integer rating2;

    /* ================= KPI 3 ================= */
    private String kpi3;
    private String review3;
    private Integer rating3;

    /* ================= KPI 4 ================= */
    private String kpi4;
    private String review4;
    private Integer rating4;

    /* ================= KPI 5 ================= */
    private String kpi5;
    private String review5;
    private Integer rating5;

    /* ================= COMMENTS ================= */
    private String employeeComments;
    private String managerComments;

    /* ================= INCREMENT ================= */
    private Integer annualIncrement;
}
