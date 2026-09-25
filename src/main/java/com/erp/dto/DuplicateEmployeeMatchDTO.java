package com.erp.dto;

import lombok.Builder;
import lombok.Getter;

/** An existing employee record that matches a new-hire's identification or full name. */
@Getter
@Builder
public class DuplicateEmployeeMatchDTO {
    private Long id;
    private String employeeNo;
    private String fullName;
    /** Backend enum name: ACTIVE, INACTIVE, RESIGNED, … */
    private String status;
    /** True when the record has been moved to the archive (HR Reports → Archive). */
    private boolean archived;
    private String identification;
    private String departmentName;
    /** IDENTIFICATION (same QID / ID number) or FULL_NAME (first + middle + last). */
    private String matchedBy;
}
