package com.erp.dto.appraisal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EmployeePerformanceResponseDTO {

    private Long employeeId;
    private String month;
    private Integer year;

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
}
