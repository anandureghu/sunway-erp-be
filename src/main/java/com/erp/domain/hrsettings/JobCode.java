package com.erp.domain.hrsettings;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "job_codes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_code_company_code",
                columnNames = {"company_id", "code"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;   // ENG-003

    @Column(nullable = false)
    private String title;  // Software Engineer

    @Column(nullable = false)
    private String level;  // Intern, Junior, Mid...

    @Column(name = "salary_grade", nullable = false)
    private String salaryGrade;  // G1, G2...

    @Column(name = "min_salary", precision = 15, scale = 2)
    private BigDecimal minSalary;

    @Column(name = "max_salary", precision = 15, scale = 2)
    private BigDecimal maxSalary;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Position state — VACANT (new codes), OCCUPIED (kept in sync with assignments
     * by JobCodeOccupancyService) or ON_HOLD (set by HR, with {@link #holdComment}).
     */
    @Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private com.erp.domain.enums.JobCodeStatus status =
            com.erp.domain.enums.JobCodeStatus.VACANT;

    /** Why the position is on hold (required while ON_HOLD). */
    @Column(name = "hold_comment", length = 500)
    private String holdComment;

    /** When the position was put on hold. */
    @Column(name = "hold_since")
    private java.time.LocalDate holdSince;

    /** Why it is on hold — PENDING_APPROVAL or RESERVED (only while ON_HOLD). */
    @Enumerated(EnumType.STRING)
    @Column(name = "hold_reason", length = 30)
    private com.erp.domain.enums.JobCodeHoldReason holdReason;

    // ── How the last hold was closed (kept after the code goes back to VACANT) ──

    /** APPROVED, REJECTED or RELEASED. */
    @Column(name = "hold_decision", length = 20)
    private String holdDecision;

    @Column(name = "hold_decision_note", length = 500)
    private String holdDecisionNote;

    @Column(name = "hold_decided_by", length = 150)
    private String holdDecidedBy;

    @Column(name = "hold_decided_at")
    private java.time.LocalDateTime holdDecidedAt;

    // ── Defaults copied onto the current job when this code is assigned ──

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private com.erp.domain.hr.Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "division_id")
    private com.erp.domain.hr.Division division;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_category", length = 30)
    private com.erp.domain.enums.EmploymentCategory employmentCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 30)
    private com.erp.domain.enums.EmploymentType employmentType;

    @Column(name = "work_location", length = 30)
    private String workLocation; // OFFICE | HYBRID | REMOTE

    @Column(name = "work_city", length = 100)
    private String workCity;

    @Column(name = "work_country", length = 100)
    private String workCountry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}
