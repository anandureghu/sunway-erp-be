package com.erp.dto.appraisal;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeAppraisalResponseDTO {

    private Long id;
    private Long employeeId;

    private String employeeName;
    private String employeeRole;

    private Integer year;
    private String status;
    private String month;

    private Double overallScore;

    private List<EmployeeGoalDTO> goals;

    private String employeeComments;
    private String managerComments;

    private String employeeAcknowledge;
    private String employeeRebuttal;

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}