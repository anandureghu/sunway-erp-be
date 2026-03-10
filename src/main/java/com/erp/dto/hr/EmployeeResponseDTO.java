package com.erp.dto.hr;

import com.erp.domain.security.Role;
import lombok.*;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeResponseDTO {

    // =========================
    // Core identity
    // =========================
    private Long id;
    private String employeeNo;
    private String firstName;
    private String lastName;

    // =========================
    // Profile info
    // =========================
    private String gender;
    private String prefix;
    private String status;
    private String maritalStatus;
    private LocalDate dateOfBirth;
    private LocalDate joinDate;

    // =========================
    // NEW PERSONAL FIELDS
    // =========================
    private String birthplace;
    private String hometown;
    private String nationality;
    private String religion;
    private String identification;

    // =========================
    // Contact info
    // =========================
    private String phoneNo;
    private String altPhone;
    private String email;
    private String notes;

    // =========================
    // Company / Department
    // =========================
    private Long companyId;
    private String companyName;

    private Long departmentId;
    private String departmentName;

    // =========================
    // User / Auth
    // =========================
    private Long userId;
    private String username;
    private Role role;
    private String CompanyRole;

    // =========================
    // UI / UX helpers
    // =========================
    private Boolean forcePasswordReset;
    private String imageUrl;
}