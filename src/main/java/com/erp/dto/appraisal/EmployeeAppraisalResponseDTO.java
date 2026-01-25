package com.erp.dto.appraisal;

import lombok.*;

import java.time.LocalDateTime;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeAppraisalResponseDTO {

    private Long id;
    private Long employeeId;

    private String month;
    private Integer year;

    private String jobCode;
    private String employeeComments;
    private String managerComments;

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

    private Integer rating;
    private Integer annualIncrement;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
