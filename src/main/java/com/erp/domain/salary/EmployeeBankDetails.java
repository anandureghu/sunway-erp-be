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

    @Column(nullable = false)
    private String bankBranch;

    @Column(nullable = false)
    private String accountType;

    @Column(nullable = false)
    private String accountNo;

    private String iban;

    private String country;
    private String state;
    private String city;

    @Column(length = 500)
    private String remarks;

}
