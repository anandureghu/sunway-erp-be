package com.erp.domain.salary;

import com.erp.domain.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "payroll",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payroll_employee_period",
                        columnNames = {"employee_id", "pay_period_start", "pay_period_end"}
                )
        }
)
@Getter
@Setter
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "payroll_code", nullable = false, unique = true, length = 100)
    private String payrollCode;

    @Column(name = "pay_period_start", nullable = false)
    private LocalDate payPeriodStart;

    @Column(name = "pay_period_end", nullable = false)
    private LocalDate payPeriodEnd;

    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    @Column(name = "gross_pay", nullable = false)
    private Double grossPay = 0.0;

    @Column(name = "loan_deduction", nullable = false)
    private Double loanDeduction = 0.0;

    @Column(name = "deductions", nullable = false)
    private Double deductions = 0.0;

    @Column(name = "net_payable", nullable = false)
    private Double netPayable = 0.0;

    /** Accrued end-of-service gratuity paid out on a final-settlement run (0 otherwise). */
    @Column(name = "end_of_service_compensation", nullable = false)
    private Double endOfServiceCompensation = 0.0;

    /** True when this run is an exit settlement (gratuity paid, active loans recovered in full). */
    @Column(name = "final_settlement", nullable = false)
    private boolean finalSettlement = false;

    @Column(name = "worked_hours", nullable = false)
    private Double workedHours = 0.0;

    @Column(name = "worked_days", nullable = false)
    private Double workedDays = 0.0;

    @Column(name = "paid_leave_days", nullable = false)
    private Double paidLeaveDays = 0.0;

    @Column(name = "unpaid_leave_days", nullable = false)
    private Double unpaidLeaveDays = 0.0;

    @Column(name = "payable_days", nullable = false)
    private Double payableDays = 0.0;

    @Column(name = "lop_days", nullable = false)
    private Double lopDays = 0.0;

    @Column(name = "lop_amount", nullable = false)
    private Double lopAmount = 0.0;

    @Column(name = "bank_name", nullable = false, length = 200)
    private String bankName;

    @Column(name = "bank_account", nullable = false, length = 100)
    private String bankAccount;

    @Transient
    public Integer getWorkingDays() {
        double value = safe(payableDays) + safe(lopDays);
        return (int) Math.round(value);
    }

    @Transient
    public Integer getTotalDays() {
        if (payPeriodStart == null || payPeriodEnd == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(payPeriodStart, payPeriodEnd) + 1;
        return (int) Math.max(days, 0);
    }

    @Transient
    public Integer getLeaveTaken() {
        double value = safe(paidLeaveDays) + safe(unpaidLeaveDays);
        return (int) Math.round(value);
    }

    private double safe(Double value) {
        return value != null ? value : 0.0;
    }

    @PrePersist
    @PreUpdate
    private void normalize() {
        grossPay = safe(grossPay);
        loanDeduction = safe(loanDeduction);
        deductions = safe(deductions);
        netPayable = safe(netPayable);
        endOfServiceCompensation = safe(endOfServiceCompensation);
        workedHours = safe(workedHours);
        workedDays = safe(workedDays);
        paidLeaveDays = safe(paidLeaveDays);
        unpaidLeaveDays = safe(unpaidLeaveDays);
        payableDays = safe(payableDays);
        lopDays = safe(lopDays);
        lopAmount = safe(lopAmount);
    }
}