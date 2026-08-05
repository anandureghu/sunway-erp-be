package com.erp.dto.appraisal;

import lombok.Data;
import java.util.List;

@Data
public class AppraisalRoleConfigRequestDTO {

    private String jobCode;
    private List<AppraisalGoalTemplateRequestDTO> goals;
}