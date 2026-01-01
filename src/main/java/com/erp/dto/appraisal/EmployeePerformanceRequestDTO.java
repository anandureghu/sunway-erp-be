package com.erp.dto.appraisal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmployeePerformanceRequestDTO {

    // Identifiers (set in controller)
    private Long employeeId;


    // KPI + Review fields
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

    public void setMonth(String month) {
    }

    public void setYear(Integer year) {

    }
}
