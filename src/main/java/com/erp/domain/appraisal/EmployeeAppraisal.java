package com.erp.domain.appraisal;

import com.erp.domain.Employee;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "employee_appraisals",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_employee_appraisal_month_year",
                        columnNames = {"employee_id", "year", "month"}
                )
        },
        indexes = {
                @Index(name = "idx_employee_appraisal_employee", columnList = "employee_id"),
                @Index(name = "idx_employee_appraisal_year",     columnList = "year"),
                @Index(name = "idx_employee_appraisal_month",    columnList = "month")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAppraisal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ================= RELATIONSHIPS ================= */

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private AppraisalConfig config;

    /* ================= PERIOD ================= */

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false, length = 20)
    private String month; // JANUARY, FEBRUARY ... DECEMBER

    /* ================= WORKFLOW ================= */

    @Column(length = 30)
    private String status;
    // DRAFT → SELF_SUBMITTED → MANAGER_REVIEWED → LOCKED

    /* ================= CALCULATED ================= */

    private Double overallScore;

    /* ================= COMMENTS ================= */

    @Column(columnDefinition = "TEXT")
    private String employeeComments;

    @Column(columnDefinition = "TEXT")
    private String managerComments;

    private String employeeAcknowledge; // AGREE / DISAGREE

    @Column(columnDefinition = "TEXT")
    private String employeeRebuttal;

    /* ================= GOALS ================= */

    @OneToMany(
            mappedBy   = "appraisal",
            cascade    = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<EmployeeAppraisalGoal> goals = new ArrayList<>();

    /* ================= AUDIT ================= */

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    private LocalDateTime updatedDate;
}