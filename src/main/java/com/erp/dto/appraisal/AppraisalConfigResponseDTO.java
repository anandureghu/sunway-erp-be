package com.erp.dto.appraisal;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AppraisalConfigResponseDTO {

    private Long id;
    private Integer year;
    private String cycleName;
    private String status;

    private Boolean enableSelfAssessment;
    private Boolean enableMidYear;
    private Boolean enablePIP;

    private List<RoleConfigResponseDTO> roles;
}