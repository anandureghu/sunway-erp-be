package com.erp.dto.hr.report;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** HR Reports → Single employee summary: everything HR needs on one sheet. */
@Getter
@Builder
public class EmployeeSummaryReportDTO {
    // ── identity ──
    private Long id;
    private String employeeNo;
    private String prefix;
    private String firstName;
    private String middleName;
    private String lastName;
    private String fullName;
    private String status;
    private String gender;
    private LocalDate dateOfBirth;
    private Integer age;
    private String maritalStatus;
    private String nationality;
    private String religion;
    private String identification;
    private String birthplace;
    private String hometown;

    // ── contact ──
    private String email;
    private String phone;
    private String altPhone;
    private List<Address> addresses;

    // ── current job ──
    private String departmentName;
    private String divisionName;
    private String designation;
    private String jobCode;
    private String gradeCode;
    private String employmentType;
    private String employmentCategory;
    private String workLocation;
    private String reportingManager;
    private String companyRole;
    private LocalDate joinDate;
    private LocalDate probationEndDate;
    private LocalDate expectedEndDate;
    private Double yearsOfService;
    private String serviceLabel;

    // ── contract ──
    private String contractCode;
    private String contractType;
    private String contractStatus;
    private LocalDate contractStartDate;
    private LocalDate contractEndDate;
    private Integer noticePeriodDays;

    // ── salary (null when the caller cannot see salaries) ──
    private BigDecimal grossSalary;
    private boolean salaryVisible;
    private String currencyCode;

    private String companyName;

    @Getter
    @Builder
    public static class Address {
        private String type;
        private boolean primary;
        private String text;
    }
}
