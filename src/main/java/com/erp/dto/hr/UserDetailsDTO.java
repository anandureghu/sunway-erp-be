package com.erp.dto.hr;

import com.erp.domain.security.Role;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailsDTO {

    // ── User info ─────────────────────────────────────────────────────────────
    private Long   userId;
    private String fullName;
    private String email;
    private String username;

    /** Spring Security role — use only for permission checks (hasRole, @PreAuthorize) */
    private Role   role;

    /** HR-managed dynamic role — use for appraisals, org chart, display, filtering */
    private Long companyRoleId;
    private String companyRole;

    // ── Employee info (optional — null if user has no employee record) ────────
    private Long   employeeId;
    private String employeeNo;
    private String firstName;
    private String lastName;
    private String phoneNo;
    private String imageUrl;

    // ── Company info ──────────────────────────────────────────────────────────
    private Long   companyId;
    private String companyName;

    // ── Department info ───────────────────────────────────────────────────────
    private Long   departmentId;
    private String departmentName;
}
