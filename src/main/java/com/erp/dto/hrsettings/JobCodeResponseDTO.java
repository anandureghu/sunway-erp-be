package com.erp.dto.hrsettings;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Builder
public class JobCodeResponseDTO {

    private Long id;
    private String code;
    private String title;
    private String level;
    private String salaryGrade;
    private BigDecimal minSalary;
    private BigDecimal maxSalary;
    private Boolean active;
    private Long companyId;

    /**
     * Assignability for the Current Job picker (only populated by /assignable):
     * false when the code is already held by another still-employed person, in
     * which case `assignedTo` names that holder. Null elsewhere (treated as free).
     */
    @Setter
    private Boolean assignable;
    @Setter
    private String assignedTo;
}
