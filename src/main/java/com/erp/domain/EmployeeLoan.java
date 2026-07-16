package com.erp.domain;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(
        name = "employee_loans",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_employee_loans_company_loan_code", columnNames = {"company_id", "loan_code"})
        }
)
@Getter
@Setter
public class EmployeeLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
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

    /** Archived decided loans drop out of the active Loan Approvals list. */
    @Column(nullable = false)
    private boolean archived = false;

    @Column(length = 1000)
    private String notes;

    /** Approver's reason when the loan is rejected. */
    @Column(length = 1000)
    private String rejectionComment;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;
}