package com.erp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "employee_loans")
@Getter
@Setter
public class EmployeeLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false, unique = true)
    private String loanCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoanType loanType;

    @Column(nullable = false)
    private Double loanAmount;

    @Column(nullable = false)
    private Integer loanPeriod; // months

    @Column(nullable = false)
    private Double monthlyDeduction;

    @Column(nullable = false)
    private Double balance;

    @Column(nullable = false)
    private String status; // ACTIVE, CLOSED

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;
}