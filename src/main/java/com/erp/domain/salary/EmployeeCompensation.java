package com.erp.domain.salary;

import com.erp.domain.Employee;
import com.erp.domain.enums.BenefitType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
@Entity
@Table(name = "employee_compensation")
@Getter
@Setter
public class EmployeeCompensation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    // Salary
    @Column(nullable = false)
    private Double basicSalary;

    // Transportation
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BenefitType transportationType;

    @Column(nullable = false)
    private Double transportationAllowance;

    // Travel
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BenefitType travelType;

    @Column(nullable = false)
    private Double travelAllowance;

    // Housing
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BenefitType housingType;

    @Column(nullable = false)
    private Double housingAllowance;

    // Other
    @Column(nullable = false)
    private Double otherAllowance;

    @Column(nullable = false)
    private Double totalCompensation;

    @Column(nullable = false, length = 20)
    private String status; // ACTIVE / INACTIVE

    @Column(nullable = false)
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;
}
