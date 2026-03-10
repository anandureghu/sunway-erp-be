package com.erp.dto.appraisal;

import lombok.Data;

@Data
public class AppraisalGoalTemplateRequestDTO {

    private String kpi;
    private String description;
    private Integer weight;
    private Boolean active;
}