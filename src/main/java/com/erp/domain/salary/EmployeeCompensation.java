package com.erp.domain.salary;

import com.erp.domain.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "employee_compensation")
public class EmployeeCompensation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ================= RELATION ================= */

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /* ================= SALARY ================= */

    @Setter
    @Column(nullable = false)
    private Double basicSalary;

    /* ================= TRANSPORTATION ================= */

    @Setter
    @Column(nullable = false)
    private Boolean transportation;

    @Setter
    @Column(nullable = false)
    private Double transportationAllowance;

    /* ================= TRAVEL ================= */

    @Setter
    @Column(nullable = false)
    private Boolean travel;

    @Setter
    @Column(nullable = false)
    private Double travelAllowance;

    /* ================= HOUSING ================= */

    @Setter
    @Column(nullable = false)
    private Boolean housing;

    @Setter
    @Column(nullable = false)
    private Double housingAllowance;

    /* ================= OTHER ================= */

    @Setter
    @Column(nullable = false)
    private Double otherAllowance;

    @Setter
    @Column(nullable = false)
    private Double totalCompensation;

    /* ================= STATUS ================= */

    @Setter
    @Column(nullable = false, length = 20)
    private String status; // ACTIVE / INACTIVE

    @Setter
    @Column(nullable = false)
    private LocalDate effectiveFrom;

    @Setter
    private LocalDate effectiveTo;
}
