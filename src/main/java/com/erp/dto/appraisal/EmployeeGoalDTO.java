package com.erp.dto.appraisal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class EmployeeGoalDTO {

    private Long goalId;   // Required for update (maps to EmployeeAppraisalGoal.id)

    private String kpi;
    private String description;
    private Integer weight;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer selfRating;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must be at most 5")
    private Integer managerRating;

    private String selfComment;
    private String managerComment;
}