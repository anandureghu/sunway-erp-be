package com.erp.domain;

import com.erp.domain.hr.Company;
import com.erp.domain.hr.CompanyRole;
import com.erp.domain.hr.Department;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "employees",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_employee_user_company",
                columnNames = {"user_id", "company_id"}
        )
)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_no", unique = true, nullable = false)
    private String employeeNo;

    @Column(name = "first_name", length = 50, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 50, nullable = false)
    private String lastName;

    @Column(length = 20)
    private String prefix;

    /**
     * MALE | FEMALE | OTHER
     */
    @Column(length = 20)
    private String gender;

    /**
     * ACTIVE | INACTIVE | ON_LEAVE
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private EmployeeStatus status;

    private LocalDate dateOfBirth;

    @Column(length = 30)
    private String maritalStatus;

    private LocalDate joinDate;

    /* ================= PERSONAL FIELDS ================= */

    @Column(length = 100)
    private String birthplace;

    @Column(length = 100)
    private String hometown;

    @Column(length = 100)
    private String nationality;

    @Column(length = 100)
    private String religion;

    @Column(length = 100)
    private String identification;

    /* ================= OTHER FIELDS ================= */

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "image_url")
    private String imageUrl;

    /**
     * Employee's security role (synchronized with user.role)
     * ✅ Used for leave policy lookups (instead of deriving from user)
     * Examples: USER, ADMIN, MANAGER
     */
    @Column(name = "role", length = 50)
    private String role;

    /* ================= RELATIONS ================= */

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /**
     * Linked User (global security role lives on User)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Per-company HR role (Team Lead, HR Manager, etc.) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_role_id")
    private CompanyRole companyRoleRef;

    @OneToOne(
            mappedBy = "employee",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private EmployeeContactInfo contactInfo;

    /* ================= AUDIT ================= */

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();

        if (this.status == null) {
            this.status = EmployeeStatus.ACTIVE;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /* ================= HELPER METHODS ================= */

    public String getPhoneNo() {
        return contactInfo != null ? contactInfo.getPhone() : null;
    }

    public String getAltPhone() {
        return contactInfo != null ? contactInfo.getAltPhone() : null;
    }

    public String getEmail() {
        return user != null ? user.getEmail() : null;
    }

    /**
     * ✅ Get role from the role field (for leave policies)
     * Falls back to user.role if role field is null
     */
    public String getRole() {
        // First try the explicit role field
        if (this.role != null && !this.role.isBlank()) {
            return this.role;
        }
        // Fall back to user's role
        return user != null && user.getRole() != null
                ? user.getRole().name()
                : null;
    }

    /**
     * ✅ Set role on the role field
     * This allows leave policies to use the role for lookups
     */
    public void setRole(String name) {
        this.role = name;
    }

    public Long getCompanyRoleId() {
        return companyRoleRef != null ? companyRoleRef.getId() : null;
    }

    /**
     * Company Role (USED FOR PERMISSIONS) — per-company on Employee.
     */
    public String getCompanyRole() {
        if (companyRoleRef != null && companyRoleRef.getName() != null && !companyRoleRef.getName().isBlank()) {
            return companyRoleRef.getName().trim();
        }
        // Legacy fallback during migration
        return (user != null && user.getCompanyRole() != null && !user.getCompanyRole().isBlank())
                ? user.getCompanyRole().trim()
                : null;
    }

    /**
     * 🔹 Company ID helper (useful everywhere)
     */
    public Long getCompanyId() {
        return company != null ? company.getId() : null;
    }
}