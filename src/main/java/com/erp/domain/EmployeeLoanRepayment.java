package com.erp.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One repayment of an employee loan (payroll instalment, final settlement or manual payment). */
@Entity
@Table(name = "employee_loan_repayments")
@Getter
@Setter
public class EmployeeLoanRepayment {

    public static final String SOURCE_PAYROLL = "PAYROLL";
    public static final String SOURCE_SETTLEMENT = "SETTLEMENT";
    public static final String SOURCE_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** First day of the month the repayment belongs to. */
    @Column(name = "payment_month", nullable = false)
    private LocalDate paymentMonth;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "source", nullable = false, length = 20)
    private String source = SOURCE_PAYROLL;

    @Column(name = "payroll_id")
    private Long payrollId;

    /** Payroll code for payroll repayments. */
    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
