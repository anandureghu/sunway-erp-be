package com.erp.dto.appraisal;

import lombok.Data;

@Data
public class AppraisalGoalTemplateResponseDTO {

    private Long id;
    private String kpi;
    private String description;
    private Integer weight;
    private Boolean active;
}