package com.erp.domain;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "company_leave_policies",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"company_id", "job_code", "leave_type"}
        )
)
public class CompanyLeavePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * The JOB CODE this policy applies to (matched against the employee's current
     * job code). Historically held a company/security role name; such legacy rows
     * still resolve via {@link com.erp.service.LeavePolicyKeyResolver}'s fallback.
     */
    @Column(name = "job_code", nullable = false)
    private String jobCode;

    @Column(name = "leave_type", nullable = false)
    private String leaveType;

    @Column(name = "default_days", nullable = false)
    private Integer defaultDays;

    @Column(nullable = false)
    private Boolean paid = true;

    @Column(name = "gender_restricted", nullable = false)
    private Boolean genderRestricted = false;

    @Column(name = "allowed_gender")
    private String allowedGender;

    @Column(name = "religion_restricted", nullable = false)
    private Boolean religionRestricted = false;

    @Column(name = "allowed_religion")
    private String allowedReligion;

    // ✅ Safe boolean getter (prevents NullPointerException)
    public boolean isPaid() {
        return Boolean.TRUE.equals(this.paid);
    }

    public boolean isGenderRestricted() {
        return Boolean.TRUE.equals(this.genderRestricted);
    }

    public boolean isReligionRestricted() {
        return Boolean.TRUE.equals(this.religionRestricted);
    }
}
