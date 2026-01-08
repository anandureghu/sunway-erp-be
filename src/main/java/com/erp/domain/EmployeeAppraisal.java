package com.erp.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "employee_appraisals",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_employee_appraisal_period",
                        columnNames = {"employee_id", "month", "year"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_employee_appraisals_employee",
                        columnList = "employee_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeAppraisal {

    /* =====================
       PRIMARY KEY
    ====================== */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* =====================
       RELATIONSHIP
    ====================== */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "employee_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_employee_appraisal_employee")
    )
    private Employee employee;

    /* =====================
       PERIOD (UI)
    ====================== */
    @NotNull
    @Column(name = "month", nullable = false, length = 20)
    private String month; // stored lowercase (january, february…)

    @NotNull
    @Min(1900)
    @Max(2100)
    @Column(name = "year", nullable = false)
    private Integer year;

    /* =====================
       JOB INFO
    ====================== */
    @Column(name = "job_code", length = 50)
    private String jobCode;

    /* =====================
       KPIs
    ====================== */
    @Column(name = "kpi1")
    private String kpi1;

    @Column(name = "review1", columnDefinition = "TEXT")
    private String review1;

    @Column(name = "kpi2")
    private String kpi2;

    @Column(name = "review2", columnDefinition = "TEXT")
    private String review2;

    @Column(name = "kpi3")
    private String kpi3;

    @Column(name = "review3", columnDefinition = "TEXT")
    private String review3;

    @Column(name = "kpi4")
    private String kpi4;

    @Column(name = "review4", columnDefinition = "TEXT")
    private String review4;

    @Column(name = "kpi5")
    private String kpi5;

    @Column(name = "review5", columnDefinition = "TEXT")
    private String review5;

    /* =====================
       COMMENTS
    ====================== */
    @Column(name = "employee_comments", columnDefinition = "TEXT")
    private String employeeComments;

    @Column(name = "manager_comments", columnDefinition = "TEXT")
    private String managerComments;

    /* =====================
       RATING / INCREMENT
    ====================== */
    @Min(1)
    @Max(5)
    @Column(name = "rating")
    private Integer rating;

    @Min(0)
    @Column(name = "annual_increment")
    private Integer annualIncrement;

    /* =====================
       AUDIT
    ====================== */
    @CreationTimestamp
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    @Column(name = "updated_date")
    private LocalDateTime updatedDate;
}
