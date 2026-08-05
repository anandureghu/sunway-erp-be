package com.erp.dto.appraisal;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RoleConfigResponseDTO {

    private String jobCode;
    private List<EmployeeGoalDTO> goals;
}