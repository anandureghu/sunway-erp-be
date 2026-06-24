package com.erp.domain;

import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyRole;
import com.erp.domain.security.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email",    columnNames = "email"),
        @UniqueConstraint(name = "uk_users_username", columnNames = "username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"company", "companyRoleRef"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role = Role.USER;

    // 🔹 Company role (Team Lead, HR Manager, etc.)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_role_id")
    private CompanyRole companyRoleRef;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "force_password_reset", nullable = false)
    private Boolean forcePasswordReset = true;

    @Column(name = "two_factor_enabled", nullable = false)
    private Boolean twoFactorEnabled = false;

    // 🔹 Company relation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    // ✅ FIXED METHOD
    public Long getCompanyId() {
        return company != null ? company.getId() : null;
    }

    public Long getCompanyRoleId() {
        return companyRoleRef != null ? companyRoleRef.getId() : null;
    }

    public String getCompanyRole() {
        return companyRoleRef != null ? companyRoleRef.getName() : null;
    }
}
