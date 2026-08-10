package com.erp.domain;

import com.erp.domain.hr.Company;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * A manually-entered overtime figure for one employee for one month. Used only by
 * companies that do NOT punch in/out — with no timesheets to derive overtime from,
 * HR keys the hours in the Employee Time Sheets tab and they flow into payroll.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "employee_overtime_override",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ot_override_emp_period",
                columnNames = {"employee_id", "period_year", "period_month"}))
public class EmployeeOvertimeOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "period_year", nullable = false)
    private int year;

    @Column(name = "period_month", nullable = false)
    private int month;

    @Column(name = "overtime_hours", nullable = false)
    private double overtimeHours;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void touch() {
        updatedAt = Instant.now();
    }
}
