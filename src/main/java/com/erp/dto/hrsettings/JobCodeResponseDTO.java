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
    /** Position state: VACANT | OCCUPIED | ON_HOLD. */
    private String status;
    /** Why the position is on hold (only while ON_HOLD). */
    private String holdComment;
    private java.time.LocalDate holdSince;
    /** PENDING_APPROVAL | RESERVED (only while ON_HOLD). */
    private String holdReason;
    /** How the last hold was closed: APPROVED | REJECTED | RELEASED. */
    private String holdDecision;
    private String holdDecisionNote;
    private String holdDecidedBy;
    private java.time.LocalDateTime holdDecidedAt;
    /** Who holds the position (only while OCCUPIED). */
    @Setter
    private String occupiedBy;

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
