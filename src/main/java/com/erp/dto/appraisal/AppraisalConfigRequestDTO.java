package com.erp.dto.appraisal;

import lombok.Data;
import java.util.List;

@Data
public class AppraisalConfigRequestDTO {

    private Integer year;
    private String cycleName;

    private Boolean enableSelfAssessment;
    private Boolean enableMidYear;
    private Boolean enablePIP;

    private List<AppraisalRoleConfigRequestDTO> roles;
}