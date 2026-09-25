package com.erp.domain.salary;

import com.erp.domain.Employee;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A one-off benefit (annual ticket, bonus, reimbursement) granted to an employee.
 * It stays PENDING until the payroll run covering its pay month pays it, then it is
 * marked PAID and linked to that payroll.
 */
@Entity
@Table(name = "employee_benefit_grants")
@Getter
@Setter
public class EmployeeBenefitGrant {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_type", nullable = false, length = 30)
    private BenefitGrantType benefitType;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    /** First day of the month whose payroll pays this grant. */
    @Column(name = "pay_month", nullable = false)
    private LocalDate payMonth;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "document_path", length = 500)
    private String documentPath;

    @Column(name = "status", nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "payroll_id")
    private Long payrollId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = STATUS_PENDING;
        }
    }
}
