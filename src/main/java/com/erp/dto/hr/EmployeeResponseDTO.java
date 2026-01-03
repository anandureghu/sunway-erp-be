package com.erp.dto.hr;

import com.erp.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    // Profile info (FIXED)
    // =========================
    private String gender;
    private String prefix;
    private String status;
    private String maritalStatus;
    private LocalDate dateOfBirth;
    private LocalDate joinDate;

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
}
