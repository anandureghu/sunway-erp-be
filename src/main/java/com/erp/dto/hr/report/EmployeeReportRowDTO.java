package com.erp.dto.hr.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One line of the HR Reports → Employee register (all employees). */
@Getter
@Builder
public class EmployeeReportRowDTO {
    private Long id;
    private String employeeNo;
    private String firstName;
    private String middleName;
    private String lastName;
    private String fullName;
    /** Backend enum name: ACTIVE, UNDER_PROBATION, ON_LEAVE, RESIGNED, … */
    private String status;
    private String nationality;
    private String departmentName;
    private String divisionName;
    /** Job title from the current job's job code. */
    private String designation;
    private String jobCode;
    /** Salary grade from the job code (e.g. G2). */
    private String gradeCode;
    private LocalDate joinDate;
    /** Completed years of service as a decimal (e.g. 8.3). */
    private Double yearsOfService;
    /** "8 years 4 months". */
    private String serviceLabel;
    /** Contract type from the latest contract (PERMANENT, TEMPORARY, …). */
    private String contractType;
    /** Employment category from the current job (PERMANENT, CONTRACT, …) — fallback. */
    private String employmentCategory;
    /** Monthly gross (total compensation); null when the caller cannot see salaries. */
    private BigDecimal grossSalary;

    // ── used by the Workforce Overview (age mix, anniversaries, watch-list) ──
    private String gender;
    private LocalDate dateOfBirth;
    private LocalDate probationEndDate;
    /** Last working day of an exiting employee (current job's expected end date). */
    private LocalDate expectedEndDate;
    /** End of the current contract (contract expiry, else the job's contract end). */
    private LocalDate contractEndDate;
}
