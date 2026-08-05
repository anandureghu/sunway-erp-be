package com.erp.dto.appraisal;

import lombok.Data;
import java.util.List;

@Data
public class AppraisalConfigRequestDTO {

    private Integer year;
    private String cycleName;

    private String startMonth;   // ← was missing — DB NOT NULL
    private String endMonth;     // ← was missing — DB NOT NULL

    private Integer minGoals;
    private Integer maxGoals;

    private Boolean enableSelfAssessment;
    private Boolean enableMidYear;
    private Boolean enablePIP;

    private List<AppraisalRoleConfigRequestDTO> jobConfigs;
    private List<RatingScaleItemDTO> ratingScale;
    private List<PhaseItemDTO> phases;
}