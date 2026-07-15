package com.erp.dto.dashboard.hr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrDepartmentDistributionDTO {

    private Long departmentId;
    private String departmentName;
    private long employeeCount;
    private BigDecimal percent;
}
