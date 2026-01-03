package com.erp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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
    private Employee employee;

    @Column(nullable = false)
    private String loanCode;

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

    @Column(nullable = false)
    private LocalDate startDate;
}
