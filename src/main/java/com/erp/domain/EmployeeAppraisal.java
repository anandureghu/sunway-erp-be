package com.erp.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "employee_appraisals",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "month", "year"})
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    private String month;
    private Integer year;

    // KPIs
    private String kpi1;
    private String review1;

    private String kpi2;
    private String review2;

    private String kpi3;
    private String review3;

    private String kpi4;
    private String review4;

    private String kpi5;
    private String review5;

    // Appraisal Form
    private String jobCode;

    @Column(columnDefinition = "TEXT")
    private String employeeComments;

    @Column(columnDefinition = "TEXT")
    private String managerComments;
}
