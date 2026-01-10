package com.erp.domain;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(
        name = "company_leave_policies",
        uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "leave_type"})
)
public class CompanyLeavePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "leave_type", nullable = false)
    private String leaveType; // ANNUAL, SICK, EMERGENCY, UNPAID

    @Column(name = "default_days", nullable = false)
    private Integer defaultDays;

    @Column(name = "paid", nullable = false)
    private boolean paid;
}