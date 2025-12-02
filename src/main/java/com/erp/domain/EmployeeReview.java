package com.erp.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "employee_reviews")
public class EmployeeReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many reviews per employee
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // ------------------------------
    // PERFORMANCE SECTION
    // ------------------------------
    @Column(length = 20)
    private String month;

    @Column(length = 10)
    private String year;

    @Column(length = 500) private String kpi1;
    @Column(length = 500) private String kpi2;
    @Column(length = 500) private String kpi3;
    @Column(length = 500) private String kpi4;
    @Column(length = 500) private String kpi5;

    @Column(length = 500) private String review1;
    @Column(length = 500) private String review2;
    @Column(length = 500) private String review3;
    @Column(length = 500) private String review4;
    @Column(length = 500) private String review5;

    // ------------------------------
    // APPRAISAL SECTION
    // ------------------------------
    @Column(length = 100)
    private String jobCode;

    @Column(columnDefinition = "TEXT")
    private String employeeComments;

    @Column(columnDefinition = "TEXT")
    private String managerComments;

    // Audit fields (optional)
    private LocalDate createdDate;
    private LocalDate updatedDate;
}
