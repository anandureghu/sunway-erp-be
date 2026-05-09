package com.erp.domain.salary;

import com.erp.domain.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "employee_bank_details")
public class EmployeeBankDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false)
    private String bankName;

    /** Bank short code for SIF file (e.g. QIB, CBQ). */
    @Column(name = "bank_short_name", length = 32)
    private String bankShortName;

    @Column(nullable = false)
    private String bankBranch;

    @Column(nullable = false)
    @Convert(converter = AccountTypeConverter.class)
    private AccountType accountType;

    @Column(nullable = false)
    private String accountNo;

    /** International Bank Account Number (payroll / SIF). */
    @Column(name = "iban", length = 64)
    private String iban;

    private String country;
    private String state;
    private String city;

    @Column(length = 500)
    private String remarks;

}
