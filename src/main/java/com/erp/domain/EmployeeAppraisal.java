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

    /* ================= PRIMARY KEY ================= */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ================= RELATIONSHIP ================= */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "employee_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_employee_appraisal_employee")
    )
    private Employee employee;

    /* ================= PERIOD ================= */
    @Column(nullable = false, length = 20)
    private String month;

    @Column(nullable = false)
    private Integer year;

    /* ================= JOB INFO ================= */
    private String jobCode;

    /* ================= KPI 1 ================= */
    private String kpi1;

    @Column(columnDefinition = "TEXT")
    private String review1;

    @Min(1) @Max(5)
    private Integer rating1;

    /* ================= KPI 2 ================= */
    private String kpi2;

    @Column(columnDefinition = "TEXT")
    private String review2;

    @Min(1) @Max(5)
    private Integer rating2;

    /* ================= KPI 3 ================= */
    private String kpi3;

    @Column(columnDefinition = "TEXT")
    private String review3;

    @Min(1) @Max(5)
    private Integer rating3;

    /* ================= KPI 4 ================= */
    private String kpi4;

    @Column(columnDefinition = "TEXT")
    private String review4;

    @Min(1) @Max(5)
    private Integer rating4;

    /* ================= KPI 5 ================= */
    private String kpi5;

    @Column(columnDefinition = "TEXT")
    private String review5;

    @Min(1) @Max(5)
    private Integer rating5;

    /* ================= CALCULATED ================= */
    private Double overallPerformance;   // average of rating1–rating5

    /* ================= COMMENTS ================= */
    @Column(columnDefinition = "TEXT")
    private String employeeComments;

    @Column(columnDefinition = "TEXT")
    private String managerComments;

    /* ================= INCREMENT ================= */
    private Integer annualIncrement;

    /* ================= AUDIT ================= */
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdDate;

    @UpdateTimestamp
    private LocalDateTime updatedDate;
}
