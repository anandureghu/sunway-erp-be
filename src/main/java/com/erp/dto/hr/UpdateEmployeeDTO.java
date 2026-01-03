package com.erp.dto.hr;

import com.erp.domain.EmployeeStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateEmployeeDTO {

    // =============================
    // Core
    // =============================
    private String employeeNo;
    private String firstName;
    private String lastName;
    private String prefix;
    private String gender;

    /**
     * ACTIVE | INACTIVE | ON_LEAVE
     * Optional → if null, existing status remains unchanged
     */
    private EmployeeStatus status;

    // =============================
    // Dates
    // =============================
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateOfBirth;

    private String maritalStatus;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate joinDate;

    // =============================
    // Misc
    // =============================
    private String notes;

    // =============================
    // Contact Info
    // =============================
    private String phoneNo;
    private String altPhone;
    private String email;

    // =============================
    // Relations
    // =============================
    private Long departmentId;
}
