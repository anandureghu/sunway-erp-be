package com.erp.dto.hr;

import com.erp.domain.EmployeeStatus;
import com.erp.domain.security.Role;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEmployeeDTO {

    // ── Core ──────────────────────────────────────────────────────────────────
    private String         employeeNo;
    private String         firstName;
    private String         middleName;
    private String         lastName;
    private String         prefix;
    private String         gender;
    private EmployeeStatus status;

    // ── Dates ─────────────────────────────────────────────────────────────────
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;
    private String    maritalStatus;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate joinDate;

    // ── Personal ──────────────────────────────────────────────────────────────
    private String birthplace;
    private String hometown;
    private String nationality;
    private String religion;
    private String identification;

    // ── Misc ──────────────────────────────────────────────────────────────────
    private String notes;

    // ── Contact ───────────────────────────────────────────────────────────────
    private String phoneNo;
    private String altPhone;
    private String email;

    // ── Relations ─────────────────────────────────────────────────────────────
    private Long departmentId;
    private Long companyRoleId;

    // ── Roles ─────────────────────────────────────────────────────────────────
    /** HR-managed dynamic role — e.g. "HR Manager", "Finance Lead" */
    @JsonAlias("CompanyRole")
    private String companyRole;

    /** Spring Security role — ADMIN/HR/USER etc. Only updatable by ADMIN */
    private Role role;
}
