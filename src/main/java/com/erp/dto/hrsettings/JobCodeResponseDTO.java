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
    /** Approval state: PENDING_APPROVAL | APPROVED | REJECTED. */
    private String status;

    // ── Defaults copied onto the current job when this code is assigned ──
    private Long departmentId;
    private String departmentName;
    private Long divisionId;
    private String divisionName;
    private String employmentCategory;
    private String employmentType;
    private String workLocation;
    private String workCity;
    private String workCountry;

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
