package com.erp.domain.salary;

import com.erp.domain.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "payroll")
@Getter @Setter
public class Payroll {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Employee employee;

    private String payrollCode;

    private LocalDate payPeriodStart;
    private LocalDate payPeriodEnd;
    private LocalDate payDate;

    private Double grossPay;
    private Double loanDeduction;
    private Double deductions;
    private Double netPayable;

    private String bankName;
    private String bankAccount;
}
