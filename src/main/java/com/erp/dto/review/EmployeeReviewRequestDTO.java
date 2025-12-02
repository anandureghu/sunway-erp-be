package com.erp.dto.review;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeReviewRequestDTO {

    private Long employeeId;

    // Performance
    private String month;
    private String year;

    private String kpi1;
    private String kpi2;
    private String kpi3;
    private String kpi4;
    private String kpi5;

    private String review1;
    private String review2;
    private String review3;
    private String review4;
    private String review5;

    // Appraisal
    private String jobCode;
    private String employeeComments;
    private String managerComments;
}
